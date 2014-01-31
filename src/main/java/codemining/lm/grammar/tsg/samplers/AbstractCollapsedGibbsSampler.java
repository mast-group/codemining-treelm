package codemining.lm.grammar.tsg.samplers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.apache.commons.lang.math.RandomUtils;

import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.util.SettingsLoader;
import codemining.util.StatsUtil;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.base.Function;
import com.google.common.collect.BoundType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SortedMultiset;
import com.google.common.util.concurrent.AtomicDouble;

public abstract class AbstractCollapsedGibbsSampler implements Serializable {

	public static class SampleStats {
		final double log2Prob;
		final int totalNodes;

		public SampleStats(final double logProb, final int totalNodes) {
			this.log2Prob = logProb;
			this.totalNodes = totalNodes;
		}

		@Override
		public String toString() {
			return "Log-Prob: " + String.format("%.2f", log2Prob) + " ("
					+ totalNodes + " nodes with avg log-prob "
					+ String.format("%.3f", log2Prob / totalNodes) + ")";
		}

	}

	private static final Logger LOGGER = Logger
			.getLogger(AbstractCollapsedGibbsSampler.class.getName());

	private static final long serialVersionUID = 1787249023145790854L;

	/**
	 * The interval (number of iterations) when corpus log-probability and
	 * statistics are calculated. Note that this parameter has an effect only if
	 * CollapsedGibbsSampler.CalculateLogProb=True or
	 * CollapsedGibbsSampler.CalculateStats=True
	 */
	public static final int CALC_INTERVAL = (int) SettingsLoader
			.getNumericSetting("LogProbCalculationInteval", 1);

	/**
	 * Does the sampler calculate the log-probability of the corpus given the
	 * TSG?
	 */
	public static final boolean CALC_LOGPROB = SettingsLoader
			.getBooleanSetting("CalculateLogProb", true);

	public static final boolean CALC_STATS = SettingsLoader.getBooleanSetting(
			"CalculateStats", true);

	public static final double BURN_IN_PCT = SettingsLoader.getNumericSetting(
			"BurninPct", .75);

	/**
	 * The grammar being mined. This represents the current sample of the
	 * grammar.
	 */
	protected final JavaFormattedTSGrammar sampleGrammar;

	/**
	 * The grammar after summing across the MCMC iteration (after burn-in).
	 */
	protected final JavaFormattedTSGrammar burninGrammar;

	/**
	 * The tree corpus where we mine TSGs from.
	 */
	protected List<TreeNode<TSGNode>> treeCorpus = new ArrayList<TreeNode<TSGNode>>();

	/**
	 * Constructor parameters
	 * 
	 * @param avgTreeSize
	 * @param DPconcentration
	 * @param grammar
	 */
	public AbstractCollapsedGibbsSampler(
			final JavaFormattedTSGrammar sampleGrammar,
			final JavaFormattedTSGrammar allSamplesGrammar) {
		checkArgument(sampleGrammar.getTreeExtractor() == allSamplesGrammar
				.getTreeExtractor());
		this.sampleGrammar = sampleGrammar;
		this.burninGrammar = allSamplesGrammar;
	}

	/**
	 * Add a single tree to be sampled.
	 * 
	 * @param tree
	 */
	public void addTree(final TreeNode<TSGNode> tree) {
		addTree(tree, false);
	}

	/**
	 * Add a single tree to the sampler. The sampler may decide to use the tree
	 * later, unless forceAdd is set to True.
	 * 
	 * @param tree
	 * @param forceAdd
	 */
	public abstract void addTree(final TreeNode<TSGNode> tree, boolean forceAdd);

	/**
	 * Calculate the log-probability of the whole corpus, given the TSG.
	 * 
	 * @return
	 */
	protected SampleStats calculateCorpusLogProb() {
		final AtomicDouble logProbSum = new AtomicDouble(0.);
		final AtomicInteger nNodes = new AtomicInteger(0);
		final ParallelThreadPool ptp = new ParallelThreadPool();
		for (final TreeNode<TSGNode> tree : treeCorpus) {
			ptp.pushTask(new Runnable() {

				@Override
				public void run() {
					logProbSum.addAndGet(calculateLog2ProbOf(tree));
					nNodes.addAndGet(TreeNode.getTreeSize(tree));
				}

			});

		}
		ptp.waitForTermination();
		return new SampleStats(logProbSum.get(), nNodes.get());
	}

	/**
	 * Calculate the log probability of the tree, given the TSG.
	 * 
	 * @param tree
	 * @return
	 */
	public double calculateLog2ProbOf(final TreeNode<TSGNode> tree) {
		final List<TreeNode<TSGNode>> rules = TSGNode.getAllRootsOf(tree);

		double logProbSum = 0;

		for (final TreeNode<TSGNode> rule : rules) {
			logProbSum += getSamplePosteriorLog2ProbabilityForTree(rule, false);
		}

		return logProbSum;
	}

	/**
	 * Get the grammar from all samples after burn-in.
	 * 
	 * @return
	 */
	public JavaFormattedTSGrammar getBurnInGrammar() {
		return burninGrammar;
	}

	/**
	 * Returns the grammar at the current sample.
	 * 
	 * @return the grammar
	 */
	public JavaFormattedTSGrammar getSampleGrammar() {
		return sampleGrammar;
	}

	/**
	 * Return the posterior probability for the tree.
	 * 
	 * @param subtree
	 * @param remove
	 * @return
	 */
	public abstract double getSamplePosteriorLog2ProbabilityForTree(
			final TreeNode<TSGNode> subtree, final boolean remove);

	public final List<TreeNode<TSGNode>> getTreeCorpus() {
		return treeCorpus;
	}

	/**
	 * Gibbs sampling the TSG n times. This function registers an
	 * 
	 * @param iterations
	 * @return the iteration that the sampling has stopped at or iterations+1 if
	 *         all iterations have been performed.
	 */
	public int performSampling(final int iterations) {
		final AtomicBoolean stop = new AtomicBoolean(false);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				stop.set(true);
			}

		});
		burninGrammar.clear();

		int currentIteration = 0;
		for (currentIteration = 0; currentIteration < iterations; currentIteration++) {
			System.out.println("=======Iteration " + currentIteration
					+ "==============");
			sampleAllTreesOnce(currentIteration, iterations, stop);
			if (CALC_LOGPROB && currentIteration % CALC_INTERVAL == 0) {
				System.out.println(calculateCorpusLogProb());
			}
			if (CALC_STATS && currentIteration % CALC_INTERVAL == 0) {
				printStats();
			}

			// Now add everything to sample, if burn-in has passed
			if (currentIteration > BURN_IN_PCT * iterations) {
				burninGrammar.addAll(sampleGrammar);
			}
			if (stop.get()) {
				LOGGER.info("Sampling interrupted.");
				break;
			}
		}

		return currentIteration;
	}

	public void printCorpusProbs() {
		for (final TreeNode<TSGNode> tree : treeCorpus) {
			System.out.println("----------------------------------------");
			System.out.println(printTreeWithRootProbabilities(tree));
			System.out.println("________________________________________");
			System.out.println(sampleGrammar.getJavaTreeExtractor()
					.getASTFromTree(TSGNode.tsgTreeToInt(tree)));
		}
	}

	/**
	 * Print statistics on stdout.
	 */
	protected void printStats() {
		final SortedMultiset<Integer> sizeDistribution = sampleGrammar
				.computeGrammarTreeSizeStats();
		System.out.println("Size Stats: 1-5:"
				+ sizeDistribution.subMultiset(0, BoundType.CLOSED, 5,
						BoundType.CLOSED).size()
				+ " 6-15:"
				+ sizeDistribution.subMultiset(6, BoundType.CLOSED, 15,
						BoundType.CLOSED).size()
				+ " 16-30:"
				+ sizeDistribution.subMultiset(16, BoundType.CLOSED, 30,
						BoundType.CLOSED).size()
				+ " >30:"
				+ sizeDistribution.subMultiset(31, BoundType.CLOSED,
						Integer.MAX_VALUE, BoundType.CLOSED).size());

		int sumOfSizes = 0;
		for (final com.google.common.collect.Multiset.Entry<Integer> sizeEntry : sizeDistribution
				.entrySet()) {
			sumOfSizes += sizeEntry.getCount() * sizeEntry.getElement();
		}

		final double avgSize = (((double) sumOfSizes) / sizeDistribution.size());
		System.out.println("Avg Tree Size: " + String.format("%.2f", avgSize));
	}

	/**
	 * Return a string with the join probabilities for each node (the bit) in
	 * the tree, given the current state of the sampler.
	 * 
	 * @param tree
	 */
	public String printTreeWithRootProbabilities(final TreeNode<TSGNode> tree) {
		final Map<TreeNode<TSGNode>, TreeNode<TSGNode>> roots = TSGNode
				.getNodeToRootMap(tree);

		return tree.toString(new Function<TreeNode<TSGNode>, String>() {

			@Override
			public String apply(final TreeNode<TSGNode> input) {
				final TreeNode<TSGNode> root = roots.get(input);
				String toAppend;
				if (root == null) {
					toAppend = "";
				} else if (input.isLeaf()) {
					toAppend = "";
				} else {
					toAppend = " (Join prob: "
							+ String.format("%.3f", probJoinAt(input, root))
							+ ")";
				}
				return sampleGrammar.getJavaTreeExtractor().getTreePrinter()
						.apply(TreeNode.create(input.getData().nodeKey, 0))
						+ toAppend;
			}
		});
	}

	/**
	 * Compute the probability of joining the nodes. Note that this is a
	 * duplicate from sampleAt, without any (eventual) side effects.
	 * 
	 * These are kept separate for computational efficiency reasons.;
	 */
	public double probJoinAt(final TreeNode<TSGNode> node,
			final TreeNode<TSGNode> root) {
		checkNotNull(node);
		checkNotNull(root);
		checkArgument(node != root,
				"The given node should not be the root but its parent root");

		final boolean previousRootStatus = node.getData().isRoot;
		node.getData().isRoot = false;
		final TreeNode<TSGNode> joinedTree = TSGNode.getSubTreeFromRoot(root);

		node.getData().isRoot = true;
		final TreeNode<TSGNode> splitTree1 = TSGNode.getSubTreeFromRoot(root);
		final TreeNode<TSGNode> splitTree2 = TSGNode.getSubTreeFromRoot(node);

		final double log2ProbJoined = getSamplePosteriorLog2ProbabilityForTree(
				joinedTree, !previousRootStatus);
		final double log2ProbSplit = getSamplePosteriorLog2ProbabilityForTree(
				splitTree1, previousRootStatus)
				+ getSamplePosteriorLog2ProbabilityForTree(splitTree2,
						previousRootStatus);

		final double joinTheshold;
		if (!Double.isInfinite(log2ProbJoined)) {
			final double splitLog2Prob = log2ProbJoined
					- StatsUtil.logSumOfExponentials(log2ProbJoined,
							log2ProbSplit);
			joinTheshold = Math.pow(2, splitLog2Prob);
		} else {
			// Split if probJoined == 0, regardless of the splitting prob.
			joinTheshold = 0;
		}
		// Revert
		node.getData().isRoot = previousRootStatus;

		return joinTheshold;
	}

	/**
	 * Sample all the trees once.
	 * 
	 * @param totalIterations
	 * @param stop
	 * 
	 */
	public void sampleAllTreesOnce(final int currentIteration,
			final int totalIterations, final AtomicBoolean stop) {
		final ParallelThreadPool ptp = new ParallelThreadPool();
		final Thread termSignalHandler = new Thread() {
			@Override
			public void run() {
				stop.set(true);
				ptp.interrupt();
			}
		};
		Runtime.getRuntime().addShutdownHook(termSignalHandler);

		final List<Runnable> samplings = Lists.newArrayList();
		for (final TreeNode<TSGNode> tree : treeCorpus) {
			samplings.add(new Runnable() {
				@Override
				public void run() {
					sampleSubTree(tree);
				}
			});
		}
		// Shuffle to add some more randomness
		Collections.shuffle(samplings);

		ptp.pushAll(samplings);
		ptp.waitForTermination();

		try {
			Runtime.getRuntime().removeShutdownHook(termSignalHandler);
		} catch (final Throwable e) {
			// Nothing here. It happens almost surely on interruption.
		}
	}

	/**
	 * Sample the given node and change status if needed.
	 * 
	 * @param node
	 *            the node where to sample (if it is a root or not)
	 * @param root
	 *            the current root of the tree.
	 * @return the probability of joining at this point.
	 */
	public double sampleAt(final TreeNode<TSGNode> node,
			final TreeNode<TSGNode> root) {
		checkNotNull(node);
		checkNotNull(root);
		checkArgument(node != root,
				"The given node should not be the root but its parent root");

		final boolean previousRootStatus = node.getData().isRoot;
		node.getData().isRoot = false;
		final TreeNode<TSGNode> joinedTree = TSGNode.getSubTreeFromRoot(root);

		node.getData().isRoot = true;
		final TreeNode<TSGNode> splitTree1 = TSGNode.getSubTreeFromRoot(root);
		final TreeNode<TSGNode> splitTree2 = TSGNode.getSubTreeFromRoot(node);

		final double log2ProbJoined = getSamplePosteriorLog2ProbabilityForTree(
				joinedTree, !previousRootStatus);
		final double log2ProbSplit = getSamplePosteriorLog2ProbabilityForTree(
				splitTree1, previousRootStatus)
				+ getSamplePosteriorLog2ProbabilityForTree(splitTree2,
						previousRootStatus);

		final double joinTheshold;
		if (!Double.isInfinite(log2ProbJoined)) {
			final double splitLog2Prob = log2ProbJoined
					- StatsUtil.logSumOfExponentials(log2ProbJoined,
							log2ProbSplit);
			joinTheshold = Math.pow(2, splitLog2Prob);
			node.getData().isRoot = RandomUtils.nextDouble() > joinTheshold;
		} else {
			// Split if probJoined == 0, regardless of the splitting prob.
			joinTheshold = 0;
			node.getData().isRoot = true;
		}

		// Add/remove trees from grammar
		if (previousRootStatus != node.getData().isRoot) {
			if (previousRootStatus) {
				checkArgument(sampleGrammar.removeTree(splitTree1));
				checkArgument(sampleGrammar.removeTree(splitTree2));
				sampleGrammar.addTree(joinedTree);
			} else {
				checkArgument(sampleGrammar.removeTree(joinedTree));
				sampleGrammar.addTree(splitTree1);
				sampleGrammar.addTree(splitTree2);
			}
		}
		return joinTheshold;
	}

	/**
	 * Perform TSG sampling on a single (full) tree. Sample each node in the
	 * tree one-by-one at random order
	 * 
	 * @param tree
	 * @return the log-prob of the sampling of the subtree.
	 */
	protected void sampleSubTree(final TreeNode<TSGNode> tree) {
		// A list of all the nodes.
		final List<TreeNode<TSGNode>> allNodes = Lists.newArrayList();

		// Maps nodes to their parent. Why an identity map? Because the objects
		// actually change during their lifetime in this map and their hash
		// also changes (when a node is resampled), but here we don't care about
		// these changes!
		final Map<TreeNode<TSGNode>, TreeNode<TSGNode>> parentMap = Maps
				.newIdentityHashMap();

		// Collect all nodes and build their parent map
		final ArrayDeque<TreeNode<TSGNode>> toVisit = new ArrayDeque<TreeNode<TSGNode>>();
		toVisit.push(tree);

		while (!toVisit.isEmpty()) {
			final TreeNode<TSGNode> currentNode = toVisit.pollFirst();

			for (final List<TreeNode<TSGNode>> childProperty : currentNode
					.getChildrenByProperty()) {
				for (final TreeNode<TSGNode> child : childProperty) {
					if (child.isLeaf()) {
						continue;
					}
					toVisit.push(child);
					parentMap.put(child, currentNode);
					allNodes.add(child);
				}
			}

		}

		// Start the sampling
		Collections.shuffle(allNodes);
		for (final TreeNode<TSGNode> node : allNodes) {
			// Find this node's next root...
			TreeNode<TSGNode> nodeRoot = checkNotNull(parentMap.get(node));
			while (!nodeRoot.getData().isRoot) {
				nodeRoot = checkNotNull(parentMap.get(nodeRoot));
			}

			// ...and do the sampling
			sampleAt(node, nodeRoot);
		}
	}

}
package codemining.lm.grammar.tsg.samplers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.math.RandomUtils;

import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.util.SettingsLoader;
import codemining.util.StatsUtil;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.BoundType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SortedMultiset;
import com.google.common.util.concurrent.AtomicDouble;

public abstract class AbstractCollapsedGibbsSampler implements Serializable {

	public static class SampleStats {
		final double logProb;
		final int totalNodes;

		public SampleStats(final double logProb, final int totalNodes) {
			this.logProb = logProb;
			this.totalNodes = totalNodes;
		}

		@Override
		public String toString() {
			return "Log-Prob: " + String.format("%.2f", logProb) + " ("
					+ totalNodes + " nodes with avg log-prob "
					+ String.format("%.3f", logProb / totalNodes) + ")";
		}

	}

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

	protected final double concentrationParameter;

	protected final double geometricProbability;

	/**
	 * The grammar being mined. This represents the current sample of the
	 * grammar.
	 */
	protected final JavaFormattedTSGrammar sampleGrammar;

	/**
	 * The grammar after summing across the MCMC iteration (after burn-in).
	 */
	protected final JavaFormattedTSGrammar allSamplesGrammar;

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
	public AbstractCollapsedGibbsSampler(final double avgTreeSize,
			final double DPconcentration,
			final JavaFormattedTSGrammar sampleGrammar,
			final JavaFormattedTSGrammar allSamplesGrammar) {
		geometricProbability = 1. / avgTreeSize;
		concentrationParameter = DPconcentration;

		checkArgument(sampleGrammar.getTreeExtractor() == allSamplesGrammar
				.getTreeExtractor());
		this.sampleGrammar = sampleGrammar;
		this.allSamplesGrammar = allSamplesGrammar;
	}

	/**
	 * Add a single tree to be sampled.
	 * 
	 * @param tree
	 */
	public abstract void addTree(final TreeNode<TSGNode> tree);

	/**
	 * Calculate the log-probability of the whole corpus, given the TSG.
	 * 
	 * @return
	 */
	private SampleStats calculateCorpusLogProb() {
		final AtomicDouble logProbSum = new AtomicDouble(0.);
		final AtomicInteger nNodes = new AtomicInteger(0);
		final ParallelThreadPool ptp = new ParallelThreadPool();
		for (final TreeNode<TSGNode> tree : treeCorpus) {
			ptp.pushTask(new Runnable() {

				@Override
				public void run() {
					logProbSum.addAndGet(calculateLogProbOf(tree));
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
	public double calculateLogProbOf(final TreeNode<TSGNode> tree) {
		final List<TreeNode<TSGNode>> rules = TSGNode.getAllRootsOf(tree);

		double logProbSum = 0;

		for (final TreeNode<TSGNode> tsgRule : rules) {
			logProbSum += getPosteriorLog2ProbabilityForTree(tsgRule, false);
		}

		return logProbSum;
	}

	/**
	 * Get the grammar from all samples after burn-in.
	 * 
	 * @return
	 */
	public JavaFormattedTSGrammar getBurnInGrammar() {
		return allSamplesGrammar;
	}

	/**
	 * Return the log2 prior probability for the tree.
	 * 
	 * @param subtree
	 * @return
	 */
	public abstract double getLog2PriorForTree(final TreeNode<TSGNode> subtree);

	/**
	 * Return the posterior probability for the tree.
	 * 
	 * @param subtree
	 * @param remove
	 * @return
	 */
	public abstract double getPosteriorLog2ProbabilityForTree(
			final TreeNode<TSGNode> subtree, final boolean remove);

	/**
	 * Returns the grammar at the current sample.
	 * 
	 * @return the grammar
	 */
	public JavaFormattedTSGrammar getSampleGrammar() {
		return sampleGrammar;
	}

	public final List<TreeNode<TSGNode>> getTreeCorpus() {
		return treeCorpus;
	}

	/**
	 * Gibbs sampling the TSG n times.
	 * 
	 * @param iterations
	 */
	public void performSampling(final int iterations) {
		allSamplesGrammar.clear();

		for (int i = 0; i < iterations; i++) {
			System.out.println("=======Iteration " + i + "==============");
			sampleAllTreesOnce(i, iterations);
			if (CALC_LOGPROB && i % CALC_INTERVAL == 0) {
				System.out.println(calculateCorpusLogProb());
			}
			if (CALC_STATS && i % CALC_INTERVAL == 0) {
				printStats();
			}

			// Now add everything to sample, if burn-in has passed
			if (i > BURN_IN_PCT * iterations) {
				allSamplesGrammar.addAll(sampleGrammar);
			}

		}
	}

	/**
	 * Print statistics on stdout.
	 */
	private void printStats() {
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
	 * Sample all the trees once.
	 * 
	 * @param totalIterations
	 *            TODO
	 */
	public void sampleAllTreesOnce(final int currentIteration,
			final int totalIterations) {
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
		final ParallelThreadPool ptp = new ParallelThreadPool();
		ptp.pushAll(samplings);
		ptp.waitForTermination();
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

		final double log2ProbJoined = getPosteriorLog2ProbabilityForTree(
				joinedTree, !previousRootStatus);
		final double log2ProbSplit = getPosteriorLog2ProbabilityForTree(
				splitTree1, previousRootStatus)
				+ getPosteriorLog2ProbabilityForTree(splitTree2,
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
package codemining.lm.grammar.tsg.samplers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.apache.commons.lang.math.RandomUtils;

import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.util.StatsUtil;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public abstract class AbstractCollapsedGibbsSampler extends AbstractTSGSampler {

	static final Logger LOGGER = Logger
			.getLogger(AbstractCollapsedGibbsSampler.class.getName());

	private static final long serialVersionUID = 1787249023145790854L;

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
		super(sampleGrammar, allSamplesGrammar);
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
					toAppend = " ( " + probJoinAt(input, root) + ")";
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
	public PointSampleStats probJoinAt(final TreeNode<TSGNode> node,
			final TreeNode<TSGNode> root) {
		checkNotNull(node);
		checkNotNull(root);
		checkArgument(node != root,
				"The given node should not be the root but its parent root");

		final PointSampleStats pss = new PointSampleStats();

		final boolean previousRootStatus = node.getData().isRoot;
		node.getData().isRoot = false;
		final TreeNode<TSGNode> joinedTree = TSGNode.getSubTreeFromRoot(root);
		pss.joinCount = sampleGrammar.countTreeOccurences(joinedTree);
		pss.thisNodeCount = sampleGrammar.countTreesWithRoot(node.getData());
		pss.thisRootCount = sampleGrammar.countTreesWithRoot(root.getData());

		node.getData().isRoot = true;
		final TreeNode<TSGNode> splitTree1 = TSGNode.getSubTreeFromRoot(root);
		pss.splitUpCount = sampleGrammar.countTreeOccurences(splitTree1);
		final TreeNode<TSGNode> splitTree2 = TSGNode.getSubTreeFromRoot(node);
		pss.splitDownCount = sampleGrammar.countTreeOccurences(splitTree2);

		final double log2ProbJoined = sampleGrammar
				.computeRulePosteriorLog2Probability(joinedTree,
						!previousRootStatus);
		final double log2ProbSplit = sampleGrammar
				.computeRulePosteriorLog2Probability(splitTree1,
						previousRootStatus)
				+ sampleGrammar.computeRulePosteriorLog2Probability(splitTree2,
						previousRootStatus);

		final double joinTheshold;
		if (!Double.isInfinite(log2ProbJoined)) {
			final double splitLog2Prob = log2ProbJoined
					- StatsUtil.log2SumOfExponentials(log2ProbJoined,
							log2ProbSplit);
			joinTheshold = Math.pow(2, splitLog2Prob);
		} else {
			// Split if probJoined == 0, regardless of the splitting prob.
			joinTheshold = 0;
		}
		// Revert
		node.getData().isRoot = previousRootStatus;
		pss.joinProbability = joinTheshold;
		return pss;
	}

	/**
	 * Sample all the trees once.
	 * 
	 * @param totalIterations
	 * @param stop
	 * 
	 */
	@Override
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

		final boolean wasRootBefore = node.getData().isRoot;
		node.getData().isRoot = false;
		final TreeNode<TSGNode> joinedTree = TSGNode.getSubTreeFromRoot(root);

		node.getData().isRoot = true;
		final TreeNode<TSGNode> splitTree1 = TSGNode.getSubTreeFromRoot(root);
		final TreeNode<TSGNode> splitTree2 = TSGNode.getSubTreeFromRoot(node);

		final double log2ProbJoined = sampleGrammar
				.computeRulePosteriorLog2Probability(joinedTree, !wasRootBefore);
		final double log2ProbSplit = sampleGrammar
				.computeRulePosteriorLog2Probability(splitTree1, wasRootBefore)
				+ sampleGrammar.computeRulePosteriorLog2Probability(splitTree2,
						wasRootBefore);

		final double joinTheshold;
		if (!Double.isInfinite(log2ProbJoined)) {
			final double splitLog2Prob = log2ProbJoined
					- StatsUtil.log2SumOfExponentials(log2ProbJoined,
							log2ProbSplit);
			joinTheshold = Math.pow(2, splitLog2Prob);
			node.getData().isRoot = RandomUtils.nextDouble() > joinTheshold;
		} else {
			// Split if probJoined == 0, regardless of the splitting prob.
			joinTheshold = 0;
			node.getData().isRoot = true;
		}

		// Add/remove trees from grammar
		if (wasRootBefore != node.getData().isRoot) {
			if (wasRootBefore) {
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
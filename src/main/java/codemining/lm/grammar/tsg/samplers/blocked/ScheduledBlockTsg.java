/**
 *
 */
package codemining.lm.grammar.tsg.samplers.blocked;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.TSGrammar;
import codemining.math.random.SampleUtils;
import codemining.util.SettingsLoader;
import codemining.util.StatsUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.math.DoubleMath;

/**
 * Perform TSG, with smart scheduling.
 *
 * @deprecated Does not work well.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
@Deprecated
public class ScheduledBlockTsg extends BlockCollapsedGibbsSampler {

	private static final long serialVersionUID = 7004454411963122059L;

	private final IdentityHashMap<TreeNode<TSGNode>, Integer> countToResample = Maps
			.newIdentityHashMap();

	public static final int MAX_UNSAMPLED_ITERATIONS = (int) SettingsLoader
			.getNumericSetting("maxUnsampledIterations", 20);

	private static final int DELAY_FACTOR = 1;

	private int totalNumNodes = 0;
	private int numNodesVisited = 0;

	private int currentIteration;

	/**
	 *
	 */
	public ScheduledBlockTsg(final double avgTreeSize, final double DPconcentration,
			final TSGrammar<TSGNode> sampleGrammar,
			final TSGrammar<TSGNode> allSamplesGrammar) {
		super(avgTreeSize, DPconcentration, sampleGrammar, allSamplesGrammar);
	}

	private boolean decrementOrRevist(final TreeNode<TSGNode> node) {
		if (!countToResample.containsKey(node)) {
			return true;
		}
		final int numIterations = countToResample.get(node);
		if (numIterations > 0) {
			countToResample.put(node, numIterations - 1);
			return false;
		} else {
			return true;
		}
	}

	@Override
	public void sampleAllTreesOnce(final int currentIteration,
			final int totalIterations, final AtomicBoolean stop) {
		this.currentIteration = currentIteration;
		super.sampleAllTreesOnce(currentIteration, totalIterations, stop);
		System.out.println("Visiting Ratio "
				+ (((double) numNodesVisited) / totalNumNodes));
		totalNumNodes = 0;
		numNodesVisited = 0;
	}

	@Override
	public void sampleAt(final TreeNode<TSGNode> node) {
		checkNotNull(node);
		totalNumNodes++;
		if (currentIteration > 2 && !decrementOrRevist(node)) {
			return;
		}
		numNodesVisited++;
		final TreeNode<TSGNode> root = checkNotNull(nodeType
				.getRootForNode(node));

		final boolean wasRootBefore = node.getData().isRoot;
		node.getData().isRoot = false;
		final TreeNode<TSGNode> joinedTree = TSGNode.getSubTreeFromRoot(root);

		node.getData().isRoot = true;
		final TreeNode<TSGNode> upperTree = TSGNode.getSubTreeFromRoot(root);
		final TreeNode<TSGNode> lowerTree = TSGNode.getSubTreeFromRoot(node);

		node.getData().isRoot = wasRootBefore; // Restore

		// Get all same type sites
		final Collection<TreeNode<TSGNode>> sameTypeNodes = nodeType
				.getSameTypeNodes(node);

		// compute m and sample
		int nSplit = 0;
		for (final TreeNode<TSGNode> sameTypeNode : sameTypeNodes) {
			if (sameTypeNode.getData().isRoot) {
				nSplit++;
			}
		}

		final double[] mProbs = sampleM(upperTree, lowerTree, joinedTree,
				sameTypeNodes.size(), nSplit);
		final int m = SampleUtils.getRandomIndex(mProbs);

		// change root bit in those sites
		final ArrayList<TreeNode<TSGNode>> allNodes = Lists
				.newArrayList(sameTypeNodes);
		Collections.shuffle(allNodes);
		for (int i = 0; i < m; i++) {
			allNodes.get(i).getData().isRoot = true;
		}
		for (int i = m; i < allNodes.size(); i++) {
			allNodes.get(i).getData().isRoot = false;
		}

		StatsUtil.normalizeLog2Probs(mProbs);
		setNodeIterationsSchedule(allNodes, mProbs[m]);

		// Add/remove rules as needed
		if (m > nSplit) { // we split more than before
			final int diff = m - nSplit;
			sampleGrammar.addTree(upperTree, diff);
			sampleGrammar.addTree(lowerTree, diff);
			checkArgument(sampleGrammar.removeTree(joinedTree, diff) >= 0);
		} else if (m < nSplit) { // we split less than before
			final int diff = nSplit - m;
			sampleGrammar.addTree(joinedTree, diff);
			checkArgument(sampleGrammar.removeTree(upperTree, diff) >= 0);
			checkArgument(sampleGrammar.removeTree(lowerTree, diff) >= 0);
		}
	}

	private void setNodeIterationsSchedule(
			final Collection<TreeNode<TSGNode>> allNodes,
			final double probabilityOfState) {
		final double probOfChange = 1. - probabilityOfState;
		final int numIterationsToRevist = (int) (-DoubleMath.log2(probOfChange)
				/ DELAY_FACTOR - 1);
		if (numIterationsToRevist > MAX_UNSAMPLED_ITERATIONS) {
			allNodes.forEach(n -> countToResample.put(n,
					MAX_UNSAMPLED_ITERATIONS));
		} else {
			allNodes.forEach(n -> countToResample.put(n, numIterationsToRevist));
		}
	}
}

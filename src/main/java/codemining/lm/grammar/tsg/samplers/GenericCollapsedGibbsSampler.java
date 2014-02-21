/**
 * 
 */
package codemining.lm.grammar.tsg.samplers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;

import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.ITsgPosteriorProbabilityComputer;
import codemining.lm.grammar.tsg.SequentialTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.math.distributions.GeometricDistribution;
import codemining.util.StatsUtil;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.TreeMultiset;
import com.google.common.math.DoubleMath;

/**
 * A generic collapsed Gibbs sampler that has a simpler prior.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class GenericCollapsedGibbsSampler extends AbstractCollapsedGibbsSampler {

	protected static class GenericTsgPosteriorComputer implements
			ITsgPosteriorProbabilityComputer<TSGNode> {

		private static final long serialVersionUID = 365942057220010212L;

		final SequentialTSGrammar sqGrammar;

		protected double concentrationParameter;

		protected double geometricProbability;

		private static final double AVG_NUM_CHILDREN = .5;

		final HashMap<SymbolProperty, Multiset<Integer>> symbolProductions;

		GenericTsgPosteriorComputer(final SequentialTSGrammar sqGrammar,
				final double avgTreeSize, final double DPconcentration) {
			this.sqGrammar = sqGrammar;
			concentrationParameter = DPconcentration;
			geometricProbability = 1. / avgTreeSize;

			symbolProductions = Maps.newHashMap();
		}

		@Override
		public double computeLog2PosteriorProbabilityOfRule(
				final TreeNode<TSGNode> subtree, final boolean remove) {
			checkNotNull(subtree);

			final Multiset<TreeNode<TSGNode>> trees = sqGrammar
					.convertTreeNode(subtree, true);

			double cumLogProbability = 0;

			for (final Entry<TreeNode<TSGNode>> tree : trees.entrySet()) {
				double nRulesCommonRoot = sqGrammar.countTreesWithRoot(tree
						.getElement().getData());
				double nRulesInGrammar = sqGrammar.countTreeOccurences(tree
						.getElement());

				checkArgument(nRulesCommonRoot >= nRulesInGrammar);
				final double log2prior = getLog2PriorForTree(subtree);

				if (nRulesInGrammar > 0 && remove) {
					nRulesInGrammar--;
					nRulesCommonRoot--;
				}
				final double log2Probability = StatsUtil.log2SumOfExponentials(
						DoubleMath.log2(nRulesInGrammar),
						DoubleMath.log2(concentrationParameter) + log2prior)
						- DoubleMath.log2(nRulesCommonRoot
								+ concentrationParameter);

				checkArgument(!Double.isNaN(log2Probability));
				checkArgument(log2Probability <= 0);
				cumLogProbability *= log2Probability * tree.getCount();
			}
			return cumLogProbability;
		}

		public double getLog2PriorForTree(final TreeNode<TSGNode> subtree) {
			checkNotNull(subtree);
			// final int treeSize = TreeNode.getTreeSize(subtree);
			final double logRuleMLE = getTreeLog2Probability(subtree);

			final double geometricLog2Prob = 0;
			// GeometricDistribution.getLog2Prob(treeSize,geometricProbability);
			return geometricLog2Prob + logRuleMLE;
		}

		private double getTreeLog2Probability(final TreeNode<TSGNode> subtree) {
			checkNotNull(subtree);

			final ArrayDeque<TreeNode<TSGNode>> toSee = new ArrayDeque<TreeNode<TSGNode>>();
			toSee.push(subtree);

			double logProbability = 0;
			while (!toSee.isEmpty()) {
				final TreeNode<TSGNode> currentNode = toSee.pop();
				final List<List<TreeNode<TSGNode>>> children = currentNode
						.getChildrenByProperty();
				for (int i = 0; i < children.size(); i++) {
					final List<TreeNode<TSGNode>> childrenForProperty = children
							.get(i);
					final boolean hasMultipleChildren = childrenForProperty
							.size() > 2;
					int childCount = 0;
					for (final TreeNode<TSGNode> child : childrenForProperty) {
						if (!child.isLeaf()) {
							toSee.push(child);
						}

						// Compute probability, only if it has multiple children
						// and
						// is not a root...
						if (!hasMultipleChildren
								|| (hasMultipleChildren && !child.getData().isRoot)) {
							childCount++;

							final Multiset<Integer> productions = symbolProductions
									.get(new SymbolProperty(currentNode
											.getData().nodeKey, i));
							final double log2Probability = DoubleMath
									.log2(((double) productions.count(child
											.getData().nodeKey))
											/ productions.size());
							checkArgument(!Double.isNaN(log2Probability));
							checkArgument(log2Probability <= 0);
							logProbability += log2Probability;
						}
					}
					logProbability += GeometricDistribution.getLog2Prob(
							childCount, AVG_NUM_CHILDREN);
				}

			}

			checkArgument(!(Double.isInfinite(logProbability) || Double
					.isNaN(logProbability)));
			return logProbability;
		}

	}

	private static class SymbolProperty {
		final int symbolId;
		final int propertyId;

		public SymbolProperty(final int symbolId, final int propertyId) {
			this.symbolId = symbolId;
			this.propertyId = propertyId;
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == this) {
				return true;
			}
			if (!(obj instanceof SymbolProperty)) {
				return false;
			}
			final SymbolProperty other = (SymbolProperty) obj;
			return other.symbolId == symbolId && propertyId == other.propertyId;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(symbolId, propertyId);
		}

		@Override
		public String toString() {
			return symbolId + ">" + propertyId;
		}

	}

	GenericTsgPosteriorComputer posteriorComputer;

	private static final long serialVersionUID = 1182242333534701379L;

	final SequentialTSGrammar sqGrammar;

	/**
	 * @param avgTreeSize
	 * @param DPconcentration
	 * @param grammar
	 */
	public GenericCollapsedGibbsSampler(final double avgTreeSize,
			final double DPconcentration, final SequentialTSGrammar grammar,
			final SequentialTSGrammar allSamplesGrammar) {
		super(grammar, allSamplesGrammar);
		sqGrammar = grammar;
		posteriorComputer = new GenericTsgPosteriorComputer(grammar,
				avgTreeSize, DPconcentration);
		sqGrammar.setPosteriorComputer(posteriorComputer);
	}

	private synchronized void addProductionsForNode(
			final TreeNode<TSGNode> currentNode) {
		final List<List<TreeNode<TSGNode>>> children = currentNode
				.getChildrenByProperty();

		for (int propertyId = 0; propertyId < children.size(); propertyId++) {
			for (final TreeNode<TSGNode> child : children.get(propertyId)) {
				final SymbolProperty from = new SymbolProperty(
						currentNode.getData().nodeKey, propertyId);

				Multiset<Integer> productions = posteriorComputer.symbolProductions
						.get(from);
				if (productions == null) {
					productions = TreeMultiset.create();
					posteriorComputer.symbolProductions.put(from, productions);
				}

				productions.add(child.getData().nodeKey);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tsg.AbstractCollapsedGibbsSampler#addTree(codemining
	 * .lm.grammar.tree.TreeNode)
	 */
	@Override
	public TreeNode<TSGNode> addTree(final TreeNode<TSGNode> tree,
			final boolean forceAdd) {
		treeCorpus.add(tree);
		updateProductions(tree);
		return tree;
	}

	public void pruneRareTrees(final int threshold) {
		sampleGrammar.prune(threshold);
	}

	private void updateProductions(final TreeNode<TSGNode> tree) {
		checkNotNull(tree);

		final ArrayDeque<TreeNode<TSGNode>> nodeUpdates = new ArrayDeque<TreeNode<TSGNode>>();
		nodeUpdates.push(tree);

		while (!nodeUpdates.isEmpty()) {
			final TreeNode<TSGNode> currentNode = nodeUpdates.pop();
			addProductionsForNode(currentNode);

			if (currentNode.getData().isRoot) {
				final TreeNode<TSGNode> subtree = TSGNode
						.getSubTreeFromRoot(currentNode);
				sampleGrammar.addTree(subtree);
			}

			for (final List<TreeNode<TSGNode>> childProperty : currentNode
					.getChildrenByProperty()) {
				for (final TreeNode<TSGNode> child : childProperty) {
					if (!child.isLeaf()) {
						nodeUpdates.push(child);
					}
				}
			}

		}
	}

}

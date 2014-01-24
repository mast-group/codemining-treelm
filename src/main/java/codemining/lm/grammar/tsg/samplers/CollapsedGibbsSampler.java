/**
 * 
 */
package codemining.lm.grammar.tsg.samplers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.math.RandomUtils;

import codemining.lm.grammar.cfg.AbstractContextFreeGrammar;
import codemining.lm.grammar.cfg.AbstractContextFreeGrammar.NodeConsequent;
import codemining.lm.grammar.cfg.ContextFreeGrammar;
import codemining.lm.grammar.cfg.ImmutableContextFreeGrammar;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.ITsgPosteriorProbabilityComputer;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.math.distributions.GeometricDistribution;
import codemining.util.SettingsLoader;
import codemining.util.StatsUtil;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.collect.Lists;
import com.google.common.math.DoubleMath;

/**
 * A collapsed Gibbs sampler as described in
 * 
 * \@inproceedings{post2009bayesian, title={Bayesian learning of a tree
 * substitution grammar}, author={Post, Matt and Gildea, Daniel},
 * booktitle={Proceedings of the ACL-IJCNLP 2009 Conference Short Papers},
 * pages={45--48}, year={2009}, organization={Association for Computational
 * Linguistics} }
 * 
 * @author Miltos Allamanis <m.allamanis@sms.ed.ac.uk>
 * 
 */
@DefaultSerializer(JavaSerializer.class)
public class CollapsedGibbsSampler extends AbstractCollapsedGibbsSampler
		implements Serializable {

	/**
	 * A CFG rule struct.
	 * 
	 */
	public static class CFGRule {
		final int root;
		final NodeConsequent ruleConsequent;

		public CFGRule(final int from, final NodeConsequent to) {
			root = from;
			ruleConsequent = to;
		}

	}

	protected static class ClassicTsgPosteriorComputer implements
			ITsgPosteriorProbabilityComputer<TSGNode> {

		private static final long serialVersionUID = -874360828121014055L;

		protected double concentrationParameter;

		protected double geometricProbability;

		/**
		 * A map containing the PCFG rules and their counts.
		 */
		protected AbstractContextFreeGrammar cfg;

		final JavaFormattedTSGrammar grammar;

		ClassicTsgPosteriorComputer(final JavaFormattedTSGrammar grammar,
				final double avgTreeSize, final double DPconcentration) {
			this.grammar = grammar;
			cfg = new ContextFreeGrammar(grammar.getTreeExtractor());
			concentrationParameter = DPconcentration;
			geometricProbability = 1. / avgTreeSize;
		}

		@Override
		public double computePosteriorProbability(final TreeNode<TSGNode> tree,
				final boolean remove) {
			checkNotNull(tree);

			double nRulesCommonRoot = grammar
					.countTreesWithRoot(tree.getData());
			double nRulesInGrammar = grammar.countTreeOccurences(tree);

			if (nRulesInGrammar > nRulesCommonRoot) { // Concurrency has bitten
														// us... Sorry no
														// guarantees, but it's
														// the
				// most probable that we just removed it...
				nRulesInGrammar = nRulesCommonRoot;
			}

			final double log2prior = getLog2PriorForTree(tree);

			if (nRulesInGrammar > 0 && remove) {
				nRulesInGrammar--;
				nRulesCommonRoot--;
			}

			final double log2Probability = StatsUtil.logSumOfExponentials(
					DoubleMath.log2(nRulesInGrammar),
					DoubleMath.log2(concentrationParameter) + log2prior)
					- DoubleMath
							.log2(nRulesCommonRoot + concentrationParameter);

			checkArgument(!Double.isNaN(log2Probability));
			checkArgument(log2Probability <= 0);
			return log2Probability;
		}

		/**
		 * Create a node consequent.
		 * 
		 * @param node
		 * @return
		 */
		public CFGRule createRuleForNode(final TreeNode<TSGNode> node) {
			final List<List<TreeNode<TSGNode>>> childrenByProperty = node
					.getChildrenByProperty();
			final NodeConsequent cons = new NodeConsequent(
					childrenByProperty.size());
			for (final List<TreeNode<TSGNode>> childProperties : childrenByProperty) {
				final List<Integer> propertyChildren = Lists
						.newArrayListWithCapacity(childProperties.size());
				cons.nodes.add(propertyChildren);
				for (final TreeNode<TSGNode> child : childProperties) {
					propertyChildren.add(postprocessIdForCFG(child));
				}
			}
			return new CFGRule(postprocessIdForCFG(node), cons);
		}

		/**
		 * Get the prior probability for this tree as given by the PCFG and the
		 * geometric distribution.
		 * 
		 * @param subtree
		 * @return
		 */
		public double getLog2PriorForTree(final TreeNode<TSGNode> subtree) {
			checkNotNull(subtree);
			final int treeSize = TreeNode.getTreeSize(subtree);
			final double logRuleMLE = getTreeCFLog2Probability(subtree);

			final double geometricLogProb = GeometricDistribution.getLog2Prob(
					treeSize, geometricProbability);
			return geometricLogProb + logRuleMLE;
		}

		/**
		 * Return the log probability of the given PCFG rule.
		 * 
		 * @param from
		 * @param to
		 * @return
		 */
		public double getLog2ProbForCFG(final CFGRule rule) {
			checkNotNull(rule);
			final double logProb = DoubleMath.log2(cfg.getMLProbability(
					rule.root, rule.ruleConsequent));

			checkArgument(
					!(Double.isInfinite(logProb) || Double.isNaN(logProb)),
					"LogProb is %s", logProb);
			return logProb;
		}

		/**
		 * Get the probability of the given subtree as seen from the PCFG.
		 * 
		 * @param subtree
		 * @return
		 */
		private double getTreeCFLog2Probability(final TreeNode<TSGNode> subtree) {
			checkNotNull(subtree);

			final ArrayDeque<TreeNode<TSGNode>> toSee = new ArrayDeque<TreeNode<TSGNode>>();
			toSee.push(subtree);

			double logProbability = 0;
			while (!toSee.isEmpty()) {
				final TreeNode<TSGNode> currentNode = toSee.pop();

				for (final List<TreeNode<TSGNode>> childProperties : currentNode
						.getChildrenByProperty()) {
					for (final TreeNode<TSGNode> child : childProperties) {
						if (!child.isLeaf()) {
							toSee.push(child);
						}
					}
				}
				final CFGRule rule = createRuleForNode(currentNode);
				final double nodeLogProb = getLog2ProbForCFG(rule);
				logProbability += nodeLogProb;
			}

			checkArgument(!(Double.isInfinite(logProbability) || Double
					.isNaN(logProbability)));
			return logProbability;
		}

		/**
		 * Return the id of the node. Useful for subclassing.
		 * 
		 * @return
		 */
		protected int postprocessIdForCFG(final TreeNode<TSGNode> node) {
			final int originalId = node.getData().nodeKey;
			return originalId;
		}
	}

	protected ClassicTsgPosteriorComputer posteriorComputer;
	protected ClassicTsgPosteriorComputer allSamplesPosteriorComputer;

	private static final long serialVersionUID = -6202164333602690548L;

	/**
	 * The size of the corpus initially. We will be adding more trees to the
	 * corpus later.
	 */
	public static final double INITIAL_TREE_CORPUS_SIZE = SettingsLoader
			.getNumericSetting("initialTreeCorpusSize", .05);

	protected List<TreeNode<TSGNode>> treesToBeAdded = new ArrayList<TreeNode<TSGNode>>();

	/**
	 * The rate of increasing the corpus size.
	 */
	public static final double TREE_CORPUS_INCREASE_PER_ITERATION = SettingsLoader
			.getNumericSetting("iterationTreeCorpusIncrease", .005);

	/**
	 * Constructor.
	 * 
	 * @param avgTreeSize
	 * @param DPconcentration
	 * @param format
	 */
	public CollapsedGibbsSampler(final double avgTreeSize,
			final double DPconcentration, final JavaFormattedTSGrammar grammar,
			final JavaFormattedTSGrammar allSamplesGrammar) {
		super(grammar, allSamplesGrammar);
		createPosteriorComputer(avgTreeSize, DPconcentration, grammar);
		grammar.setPosteriorComputer(posteriorComputer);
		allSamplesGrammar.setPosteriorComputer(allSamplesPosteriorComputer);
	}

	/**
	 * Create a single CFG rule for the given node and add it to the local CFG.
	 * 
	 * @param currentNode
	 */
	protected void addCFRuleForNode(final TreeNode<TSGNode> currentNode) {
		final CFGRule rule = posteriorComputer.createRuleForNode(currentNode);
		ContextFreeGrammar.addCFGRule(rule.root, rule.ruleConsequent,
				posteriorComputer.cfg.getInternalGrammar());

		final CFGRule rule2 = allSamplesPosteriorComputer
				.createRuleForNode(currentNode);
		ContextFreeGrammar.addCFGRule(rule2.root, rule2.ruleConsequent,
				allSamplesPosteriorComputer.cfg.getInternalGrammar());

	}

	/**
	 * Add a single tree to the corpus, updating counts where necessary.
	 * 
	 * @param tree
	 */
	@Override
	public void addTree(final TreeNode<TSGNode> tree, final boolean forceAdd) {
		final TreeNode<TSGNode> immutableTree = tree.toImmutable();
		if (forceAdd) {
			treeCorpus.add(immutableTree);
			updateTSGRuleFrequencies(immutableTree);
		} else {
			treesToBeAdded.add(immutableTree);
		}
		updateCFGRuleFrequencies(immutableTree);
	}

	/**
	 * @param avgTreeSize
	 * @param DPconcentration
	 * @param grammar
	 */
	protected void createPosteriorComputer(final double avgTreeSize,
			final double DPconcentration, final JavaFormattedTSGrammar grammar) {
		posteriorComputer = new ClassicTsgPosteriorComputer(grammar,
				avgTreeSize, DPconcentration);
		allSamplesPosteriorComputer = new ClassicTsgPosteriorComputer(
				allSamplesGrammar, avgTreeSize, DPconcentration);
	}

	public ClassicTsgPosteriorComputer getPosteriorComputer() {
		return posteriorComputer;
	}

	/**
	 * Return the probability for a given subtree.
	 * 
	 * @param subtree
	 * @param remove
	 *            remove the count of this subtree
	 * @return
	 */
	@Override
	public double getPosteriorLog2ProbabilityForTree(
			final TreeNode<TSGNode> subtree, final boolean remove) {
		return posteriorComputer.computePosteriorProbability(subtree, remove);
	}

	/**
	 * Lock the sampler data for faster sampling.
	 */
	public void lockSamplerData() {
		posteriorComputer.cfg = new ImmutableContextFreeGrammar(
				posteriorComputer.cfg);
	}

	public void pruneRareTrees(final int threshold) {
		sampleGrammar.prune(threshold);
		allSamplesGrammar.prune(threshold);
	}

	@Override
	public void sampleAllTreesOnce(final int currentIteration,
			final int totalIterations) {
		// Add new trees, if any
		final int sizeOfCorpus = (int) Math
				.ceil((INITIAL_TREE_CORPUS_SIZE * Math.pow(
						1 + TREE_CORPUS_INCREASE_PER_ITERATION,
						currentIteration))
						* (treesToBeAdded.size() + treeCorpus.size()));

		while (treeCorpus.size() < sizeOfCorpus && !treesToBeAdded.isEmpty()) {
			final int nextTreePos = RandomUtils.nextInt(treesToBeAdded.size());
			final TreeNode<TSGNode> treeToBeAdded = treesToBeAdded
					.get(nextTreePos);

			treeCorpus.add(treeToBeAdded);
			updateTSGRuleFrequencies(treeToBeAdded);
			treesToBeAdded.remove(nextTreePos);
		}
		super.sampleAllTreesOnce(currentIteration, totalIterations);
	}

	/**
	 * Recursively update tree frequencies. I.e. when a tree is added to the
	 * corpus, update the counts appropriately.
	 * 
	 * @param node
	 */
	protected void updateCFGRuleFrequencies(final TreeNode<TSGNode> node) {
		checkNotNull(node);

		final ArrayDeque<TreeNode<TSGNode>> nodeUpdates = new ArrayDeque<TreeNode<TSGNode>>();
		nodeUpdates.push(node);

		while (!nodeUpdates.isEmpty()) {
			final TreeNode<TSGNode> currentNode = nodeUpdates.pop();
			addCFRuleForNode(currentNode);

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

	/**
	 * Update the TSG rule frequencies with this tree.
	 * 
	 * @param node
	 */
	protected void updateTSGRuleFrequencies(final TreeNode<TSGNode> node) {
		checkNotNull(node);

		final ArrayDeque<TreeNode<TSGNode>> nodeUpdates = new ArrayDeque<TreeNode<TSGNode>>();
		nodeUpdates.push(node);

		while (!nodeUpdates.isEmpty()) {
			final TreeNode<TSGNode> currentNode = nodeUpdates.pop();

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

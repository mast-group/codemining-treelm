package codemining.lm.grammar.tsg.samplers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;

import cc.mallet.optimize.ConjugateGradient;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.Optimizer;
import codemining.lm.grammar.cfg.AbstractContextFreeGrammar;
import codemining.lm.grammar.cfg.AbstractContextFreeGrammar.NodeConsequent;
import codemining.lm.grammar.cfg.ContextFreeGrammar;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.ITsgPosteriorProbabilityComputer;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.samplers.CollapsedGibbsSampler.CFGRule;
import codemining.math.distributions.GeometricDistribution;
import codemining.util.SettingsLoader;
import codemining.util.StatsUtil;

import com.google.common.collect.Lists;
import com.google.common.math.DoubleMath;

/**
 * A TSG posterior computer given the sample. This class computes the
 * probabilities given a corpus.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
class ClassicTsgPosteriorComputer implements
		ITsgPosteriorProbabilityComputer<TSGNode> {

	private class HyperparameterOptimizable implements
			Optimizable.ByGradientValue {

		private static final double MIN_VAL = 10E-6;
		final Collection<TreeNode<TSGNode>> treeCorpus;

		public HyperparameterOptimizable(
				final Collection<TreeNode<TSGNode>> treeCorpus) {
			this.treeCorpus = treeCorpus;
		}

		@Override
		public int getNumParameters() {
			return 2;
		}

		@Override
		public double getParameter(final int idx) {
			if (idx == 0) {
				return concentrationParameter;
			} else if (idx == 1) {
				return geometricProbability;
			}
			throw new IllegalArgumentException("We have only 2 parameters");
		}

		@Override
		public void getParameters(final double[] params) {
			params[0] = concentrationParameter;
			params[1] = geometricProbability;
		}

		@Override
		public double getValue() {
			double logProbSum = 0;
			for (final TreeNode<TSGNode> fullTree : treeCorpus) {
				for (final TreeNode<TSGNode> tree : TSGNode
						.getAllRootsOf(fullTree)) {
					logProbSum += computePosteriorProbability(tree, true);
				}
			}
			return logProbSum;
		}

		@Override
		public void getValueGradient(final double[] gradients) {
			double concentrationGradient = 0;
			double geometricGradient = 0;

			for (final TreeNode<TSGNode> fullTree : treeCorpus) {
				for (final TreeNode<TSGNode> tree : TSGNode
						.getAllRootsOf(fullTree)) {
					final double nRulesCommonRoot = grammar
							.countTreesWithRoot(tree.getData()) - 1;
					final double nRulesInGrammar = grammar
							.countTreeOccurences(tree) - 1;

					checkArgument(nRulesCommonRoot >= nRulesInGrammar,
							"Counts are not correct");

					final int treeSize = TreeNode.getTreeSize(tree);
					final double logRuleMLE = getTreeCFLog2Probability(tree);

					final double geometricLogProb = GeometricDistribution
							.getLog2Prob(treeSize, geometricProbability);

					final double priorLog2Prob = geometricLogProb + logRuleMLE
							+ DoubleMath.log2(concentrationParameter);

					final double log2denominator = StatsUtil
							.logSumOfExponentials(
									DoubleMath.log2(nRulesInGrammar),
									priorLog2Prob);
					concentrationGradient += Math.pow(2, geometricLogProb
							+ logRuleMLE - log2denominator)
							- 1. / (nRulesCommonRoot + concentrationParameter);

					final double geomGradient = Math.pow(
							1 - geometricProbability, treeSize - 1)
							- (treeSize - 1.)
							* geometricProbability
							* Math.pow(1 - geometricProbability, treeSize - 2);

					geometricGradient += concentrationParameter
							* Math.pow(2, logRuleMLE - log2denominator)
							* geomGradient;

				}
			}

			gradients[0] = concentrationGradient / Math.log(2);
			gradients[1] = geometricGradient / Math.log(2);
		}

		private void limitParams() {
			if (concentrationParameter < MIN_VAL) {
				concentrationParameter = MIN_VAL;
			}
			if (geometricProbability >= 1) {
				geometricProbability = 1 - MIN_VAL;
			} else if (geometricProbability <= 0) {
				geometricProbability = MIN_VAL;
			}

		}

		@Override
		public void setParameter(final int idx, final double param) {
			if (idx == 0) {
				concentrationParameter = param;
			} else if (idx == 1) {
				geometricProbability = param;
			}
			limitParams();
		}

		@Override
		public void setParameters(final double[] params) {
			concentrationParameter = params[0];
			geometricProbability = params[1];
			limitParams();
		}
	}

	private static final long serialVersionUID = -874360828121014055L;

	protected double concentrationParameter;

	protected double geometricProbability;

	/**
	 * A map containing the PCFG rules and their counts.
	 */
	protected AbstractContextFreeGrammar cfg;

	final JavaFormattedTSGrammar grammar;

	static final Logger LOGGER = Logger
			.getLogger(ClassicTsgPosteriorComputer.class.getName());

	private static final boolean DO_GRADIENT_CHECK = SettingsLoader
			.getBooleanSetting("DoGradientCheck", false);

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

		double nRulesCommonRoot = grammar.countTreesWithRoot(tree.getData());
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
				- DoubleMath.log2(nRulesCommonRoot + concentrationParameter);

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
		final double logProb = DoubleMath.log2(cfg.getMLProbability(rule.root,
				rule.ruleConsequent));

		checkArgument(!(Double.isInfinite(logProb) || Double.isNaN(logProb)),
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

	public void optimizeHyperparameters(
			final Collection<TreeNode<TSGNode>> corpus) {

		final HyperparameterOptimizable optimizable = new HyperparameterOptimizable(
				corpus);

		if (DO_GRADIENT_CHECK) {
			final double[] gradient = new double[2];
			optimizable.getValueGradient(gradient);
			final double val1 = optimizable.getValue();

			final double dx = 10E-8;
			concentrationParameter += dx;
			double val2 = optimizable.getValue();
			final double empiricalConcentrationGradient = (val2 - val1) / dx;
			System.out.println("GRADIENT CHECKING (a): computed:" + gradient[0]
					+ " empirical:" + empiricalConcentrationGradient);
			concentrationParameter -= dx;

			geometricProbability += dx;
			val2 = optimizable.getValue();
			final double empiricalGeomGradient = (val2 - val1) / dx;
			System.out.println("GRADIENT CHECKING (geom): computed:"
					+ gradient[1] + " empirical:" + empiricalGeomGradient);
			geometricProbability -= dx;
		}

		final Optimizer optimizer = new ConjugateGradient(optimizable);

		try {
			optimizer.optimize();
		} catch (final IllegalArgumentException e) {
			// This exception may be thrown if L-BFGS
			// cannot step in the current direction.
			// This condition does not necessarily mean that
			// the optimizer has failed, but it doesn't want
			// to claim to have succeeded...
			LOGGER.severe("Failed to optimize: "
					+ ExceptionUtils.getFullStackTrace(e));
		}

		LOGGER.info("Converged at: " + concentrationParameter + ", "
				+ geometricProbability);
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
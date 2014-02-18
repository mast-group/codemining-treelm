/**
 * 
 */
package codemining.lm.grammar.tsg.samplers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.List;

import codemining.lm.grammar.cfg.AbstractContextFreeGrammar;
import codemining.lm.grammar.cfg.AbstractContextFreeGrammar.CFGRule;
import codemining.lm.grammar.cfg.AbstractContextFreeGrammar.NodeConsequent;
import codemining.lm.grammar.cfg.ContextFreeGrammar;
import codemining.lm.grammar.cfg.ImmutableContextFreeGrammar;
import codemining.lm.grammar.tree.ITreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.TSGNode;

import com.google.common.math.DoubleMath;

/**
 * A PCFG prior distribution. This allows nodes from trees to be converted
 * before quering the CFG.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class CFGPrior implements Serializable {

	/**
	 * An interface for classes that can create rules from nodes.
	 * 
	 */
	public static interface IRuleCreator {
		CFGRule createRuleForNode(final TreeNode<TSGNode> node);
	}

	private static final long serialVersionUID = -3738832029559271836L;

	/**
	 * A map containing the PCFG rules and their counts.
	 */
	public AbstractContextFreeGrammar cfg;

	private final IRuleCreator nodeCreator;

	public CFGPrior(final ITreeExtractor<Integer> treeExtractor,
			final IRuleCreator nodeCreator) {
		cfg = new ContextFreeGrammar(treeExtractor);
		this.nodeCreator = nodeCreator;
	}

	/**
	 * Add a single rule to the prior CFG.
	 * 
	 * @param rule
	 */
	public void addCFGRule(final CFGRule rule) {
		cfg.addCFGRule(rule.root, rule.ruleConsequent);
	}

	public void addCFGRule(final int root, final NodeConsequent ruleConsequent) {
		cfg.addCFGRule(root, ruleConsequent);
	}

	/**
	 * Recursively update tree frequencies. I.e. when a tree is added to the
	 * corpus, update the counts appropriately.
	 * 
	 * @param node
	 */
	public void addCFGRulesFrom(final TreeNode<TSGNode> node) {
		checkNotNull(node);

		final ArrayDeque<TreeNode<TSGNode>> nodeUpdates = new ArrayDeque<TreeNode<TSGNode>>();
		nodeUpdates.push(node);

		while (!nodeUpdates.isEmpty()) {
			final TreeNode<TSGNode> currentNode = nodeUpdates.pop();
			final CFGRule rule = nodeCreator.createRuleForNode(currentNode);
			cfg.addCFGRule(rule.root, rule.ruleConsequent);

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

	public AbstractContextFreeGrammar getInternalGrammar() {
		return cfg;
	}

	/**
	 * Return the log probability of the given PCFG rule.
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	public double getLog2ProbForCFG(
			final AbstractContextFreeGrammar.CFGRule rule) {
		checkNotNull(rule);
		double mlProbability = cfg.getMLProbability(rule.root,
				rule.ruleConsequent);
		if (Double.compare(mlProbability, 0) == 0) {
			mlProbability = 10E-10; // An arbitrary small probability.
		}
		final double logProb = DoubleMath.log2(mlProbability);

		checkArgument(!Double.isNaN(logProb), "LogProb is %s", logProb);
		return logProb;
	}

	/**
	 * Get the probability of the given subtree as seen from the PCFG.
	 * 
	 * @param subtree
	 * @return
	 */
	public double getTreeCFLog2Probability(final TreeNode<TSGNode> subtree) {
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
			final AbstractContextFreeGrammar.CFGRule rule = nodeCreator
					.createRuleForNode(currentNode);
			final double nodeLogProb = getLog2ProbForCFG(rule);
			logProbability += nodeLogProb;
		}

		checkArgument(!Double.isNaN(logProbability));
		return logProbability;
	}

	public void lockPrior() {
		cfg = new ImmutableContextFreeGrammar(cfg);
	}
}

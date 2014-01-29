/**
 * 
 */
package codemining.lm.grammar.tsg.samplers;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.math.RandomUtils;

import codemining.lm.grammar.cfg.AbstractContextFreeGrammar.NodeConsequent;
import codemining.lm.grammar.cfg.ContextFreeGrammar;
import codemining.lm.grammar.cfg.ImmutableContextFreeGrammar;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.util.SettingsLoader;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;

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

	public static final int HYPERPARAM_OPTIMIZATION_FREQ = (int) SettingsLoader
			.getNumericSetting("HyperparameterOptimizationFrequency", 20);

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
	public double getSamplePosteriorLog2ProbabilityForTree(
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
			final int totalIterations, final AtomicBoolean stop) {

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

		if (currentIteration % HYPERPARAM_OPTIMIZATION_FREQ == HYPERPARAM_OPTIMIZATION_FREQ - 1) {
			posteriorComputer.optimizeHyperparameters(treeCorpus);
		}
		super.sampleAllTreesOnce(currentIteration, totalIterations, stop);
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

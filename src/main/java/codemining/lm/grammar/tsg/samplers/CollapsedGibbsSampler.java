/**
 * 
 */
package codemining.lm.grammar.tsg.samplers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.math.RandomUtils;

import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.util.SettingsLoader;
import codemining.util.StatsUtil;

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
	 * Add a single tree to the corpus, updating counts where necessary.
	 * 
	 * @param tree
	 */
	@Override
	public TreeNode<TSGNode> addTree(final TreeNode<TSGNode> tree,
			final boolean forceAdd) {
		final TreeNode<TSGNode> immutableTree = tree.toImmutable();
		if (forceAdd) {
			treeCorpus.add(immutableTree);
			addTSGRulesToSampleGrammar(immutableTree);
		} else {
			treesToBeAdded.add(immutableTree);
		}
		posteriorComputer.getPrior().addCFGRulesFrom(immutableTree);
		return immutableTree;
	}

	/**
	 * Update the TSG rule frequencies with this tree.
	 * 
	 * @param node
	 */
	private void addTSGRulesToSampleGrammar(final TreeNode<TSGNode> node) {
		checkNotNull(node);
		for (final TreeNode<TSGNode> rule : TSGNode.getAllRootsOf(node)) {
			sampleGrammar.addTree(rule);
		}
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
				burninGrammar, avgTreeSize, DPconcentration);
		allSamplesPosteriorComputer.getPrior().cfg = posteriorComputer
				.getPrior().cfg;
	}

	public ClassicTsgPosteriorComputer getPosteriorComputer() {
		return posteriorComputer;
	}

	/**
	 * Lock the sampler data for faster sampling.
	 */
	public void lockSamplerData() {
		posteriorComputer.getPrior().lockPrior();
		allSamplesPosteriorComputer.getPrior().cfg = posteriorComputer
				.getPrior().cfg;
	}

	@Override
	public PointSampleStats probJoinAt(final TreeNode<TSGNode> node,
			final TreeNode<TSGNode> root) {
		// TODO: Very duplicate code...
		checkNotNull(node);
		checkNotNull(root);
		checkArgument(node != root,
				"The given node should not be the root but its parent root");

		final PointSampleStats pss = new PointSampleStats();

		final boolean wasRootBefore = node.getData().isRoot;
		node.getData().isRoot = false;
		final TreeNode<TSGNode> joinedTree = TSGNode.getSubTreeFromRoot(root);
		pss.joinCount = sampleGrammar.countTreeOccurences(joinedTree);
		pss.thisRootCount = sampleGrammar.countTreesWithRoot(root.getData());
		pss.cfgPriorProbJoin = Math.pow(2,
				posteriorComputer.getLog2PriorForTree(joinedTree));

		node.getData().isRoot = true;
		final TreeNode<TSGNode> splitTreeUp = TSGNode.getSubTreeFromRoot(root);
		pss.splitUpCount = sampleGrammar.countTreeOccurences(splitTreeUp);
		pss.cfgPriorProbSplitUp = Math.pow(2,
				posteriorComputer.getLog2PriorForTree(splitTreeUp));
		pss.thisNodeCount = sampleGrammar.countTreesWithRoot(node.getData());
		final TreeNode<TSGNode> splitTreeDown = TSGNode
				.getSubTreeFromRoot(node);
		pss.splitDownCount = sampleGrammar.countTreeOccurences(splitTreeDown);
		pss.cfgPriorProbSplitDown = Math.pow(2,
				posteriorComputer.getLog2PriorForTree(splitTreeDown));

		final double log2ProbJoined = sampleGrammar
				.computeRulePosteriorLog2Probability(joinedTree, !wasRootBefore);
		final double log2ProbSplit = sampleGrammar
				.computeRulePosteriorLog2Probability(splitTreeUp, wasRootBefore)
				+ sampleGrammar.computeRulePosteriorLog2Probability(
						splitTreeDown, wasRootBefore);

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
		node.getData().isRoot = wasRootBefore;
		pss.joinProbability = joinTheshold;
		return pss;
	}

	public void pruneRareTrees(final int threshold) {
		sampleGrammar.prune(threshold);
		burninGrammar.prune(threshold);
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
			addTSGRulesToSampleGrammar(treeToBeAdded);
			treesToBeAdded.remove(nextTreePos);
		}

		if (currentIteration % HYPERPARAM_OPTIMIZATION_FREQ == HYPERPARAM_OPTIMIZATION_FREQ - 1) {
			posteriorComputer.optimizeHyperparameters(treeCorpus);
		}
		super.sampleAllTreesOnce(currentIteration, totalIterations, stop);
	}
}

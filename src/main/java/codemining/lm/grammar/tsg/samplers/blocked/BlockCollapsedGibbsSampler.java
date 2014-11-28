/**
 *
 */
package codemining.lm.grammar.tsg.samplers.blocked;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.math3.util.ArithmeticUtils;

import codemining.lm.grammar.cfg.AbstractContextFreeGrammar.CFGRule;
import codemining.lm.grammar.cfg.AbstractContextFreeGrammar.NodeConsequent;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.ITsgPosteriorProbabilityComputer;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.TSGrammar;
import codemining.lm.grammar.tsg.samplers.AbstractTSGSampler;
import codemining.lm.grammar.tsg.samplers.CFGPrior;
import codemining.lm.grammar.tsg.samplers.CFGPrior.IRuleCreator;
import codemining.math.distributions.GeometricDistribution;
import codemining.math.random.SampleUtils;
import codemining.util.StatsUtil;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.base.Objects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.math.DoubleMath;

/**
 * A block collapsed gibbs sampler based on Type-based MCMC.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
@DefaultSerializer(JavaSerializer.class)
public class BlockCollapsedGibbsSampler extends AbstractTSGSampler implements
		IRuleCreator {

	public static final class BinomialCoefficientsParameters {
		public final int k;
		public final int n;

		public BinomialCoefficientsParameters(final int n, final int k) {
			this.n = n;
			this.k = k;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final BinomialCoefficientsParameters other = (BinomialCoefficientsParameters) obj;
			if (k != other.k) {
				return false;
			}
			if (n != other.n) {
				return false;
			}
			return true;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(k, n);
		}

	}

	public static class BlockedPosteriorComputer implements
			ITsgPosteriorProbabilityComputer<TSGNode> {

		private static final long serialVersionUID = -3517001399701308015L;
		final CFGPrior prior;
		final TSGrammar<TSGNode> grammar;

		final double geometricProbability;

		final double concentrationParameter;

		BlockedPosteriorComputer(final CFGPrior prior,
				final TSGrammar<TSGNode> grammar, final double avgTreeSize,
				final double DpConcentration) {
			this.prior = prior;
			this.grammar = grammar;
			grammar.setPosteriorComputer(this);
			geometricProbability = 1. / avgTreeSize;
			concentrationParameter = DpConcentration;
		}

		@Override
		public double computeLog2PosteriorProbabilityOfRule(
				final TreeNode<TSGNode> tree, final boolean remove) {
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
			checkArgument(
					!Double.isInfinite(log2prior) && !Double.isNaN(log2prior),
					"Prior is %s", log2prior);

			if (nRulesInGrammar > 0 && remove) {
				nRulesInGrammar--;
				nRulesCommonRoot--;
			}

			final double log2Probability = StatsUtil.log2SumOfExponentials(
					DoubleMath.log2(nRulesInGrammar),
					DoubleMath.log2(concentrationParameter) + log2prior)
					- DoubleMath
							.log2(nRulesCommonRoot + concentrationParameter);

			checkArgument(
					!Double.isNaN(log2Probability)
							&& !Double.isInfinite(log2Probability),
					"Posterior probability is %s", log2Probability);
			checkArgument(log2Probability <= 0);
			return log2Probability;
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
			final int treeSize = subtree.getTreeSize();
			final double logRuleMLE = prior.getTreeCFLog2Probability(subtree);

			final double geometricLogProb = GeometricDistribution.getLog2Prob(
					treeSize, geometricProbability);
			return geometricLogProb + logRuleMLE;
		}

		public CFGPrior getPrior() {
			return prior;
		}
	}

	private static LoadingCache<BinomialCoefficientsParameters, Double> combinationCache = CacheBuilder
			.newBuilder().maximumSize(5000)
			.build(new CacheLoader<BinomialCoefficientsParameters, Double>() {
				@Override
				public Double load(final BinomialCoefficientsParameters coeff) {
					return ArithmeticUtils.binomialCoefficientLog(coeff.n,
							coeff.k) / LN_2;
				}
			});

	private static final double LN_2 = Math.log(2);

	private static final long serialVersionUID = 8363745874521428863L;

	final BlockedPosteriorComputer samplePosteriorComputer;
	final BlockedPosteriorComputer burninPosteriorComputer;

	/**
	 * A map containing the PCFG rules and their counts.
	 */
	protected final CFGPrior prior;

	protected final NodeTypeInformation nodeType;

	static final Logger LOGGER = Logger
			.getLogger(BlockCollapsedGibbsSampler.class.getName());

	public BlockCollapsedGibbsSampler(final double avgTreeSize,
			final double DPconcentration,
			final TSGrammar<TSGNode> sampleGrammar,
			final TSGrammar<TSGNode> allSamplesGrammar) {
		super(sampleGrammar, allSamplesGrammar);
		prior = new CFGPrior(sampleGrammar.getTreeExtractor(), this);
		nodeType = new NodeTypeInformation();
		samplePosteriorComputer = new BlockedPosteriorComputer(prior,
				sampleGrammar, avgTreeSize, DPconcentration);
		burninPosteriorComputer = new BlockedPosteriorComputer(prior,
				burninGrammar, avgTreeSize, DPconcentration);
	}

	/**
	 * Allows extra data to be added to the prior (i.e. the CFG)
	 */
	public void addDataToPrior(final TreeNode<TSGNode> tree) {
		prior.addCFGRulesFrom(tree);
	}

	/**
	 * Add all the rules in the current tree to the grammar.
	 *
	 * @param immutableTree
	 */
	private void addRulesToGrammar(final TreeNode<TSGNode> immutableTree) {
		checkNotNull(immutableTree);
		for (final TreeNode<TSGNode> rule : TSGNode
				.getAllRootsOf(immutableTree)) {
			sampleGrammar.addTree(rule);
		}
	}

	@Override
	public TreeNode<TSGNode> addTree(final TreeNode<TSGNode> tree,
			final boolean forceAdd) {
		final TreeNode<TSGNode> immutableTree = tree.toImmutable();
		treeCorpus.add(immutableTree);

		prior.addCFGRulesFrom(immutableTree);
		nodeType.updateCorpusStructures(immutableTree);
		addRulesToGrammar(immutableTree);
		return immutableTree;
	}

	/**
	 * Create a single CFG rule for the given node.
	 *
	 * @param currentNode
	 * @param grammar2
	 */
	public CFGRule createCFRuleForNode(final TreeNode<Integer> currentNode) {
		final int rootId = currentNode.getData();

		final int nProperties = currentNode.nProperties();
		final NodeConsequent ruleConsequent = new NodeConsequent(nProperties);
		for (int i = 0; i < nProperties; i++) {
			final List<TreeNode<Integer>> children = currentNode
					.getChildrenByProperty().get(i);
			final int nChildren = children.size();
			ruleConsequent.nodes.add(Lists
					.<Integer> newArrayListWithCapacity(nChildren));
			for (int j = 0; j < nChildren; j++) {
				final int childNode = currentNode.getChild(j, i).getData();
				ruleConsequent.nodes.get(i).add(childNode);
			}
		}

		return new CFGRule(rootId, ruleConsequent);
	}

	@Override
	public CFGRule createRuleForNode(final TreeNode<TSGNode> node) {
		final int rootId = node.getData().nodeKey;

		final int nProperties = node.nProperties();
		final NodeConsequent ruleConsequent = new NodeConsequent(nProperties);
		for (int i = 0; i < nProperties; i++) {
			final List<TreeNode<TSGNode>> children = node
					.getChildrenByProperty().get(i);
			final int nChildren = children.size();
			ruleConsequent.nodes.add(Lists
					.<Integer> newArrayListWithCapacity(nChildren));
			for (int j = 0; j < nChildren; j++) {
				final int childNode = node.getChild(j, i).getData().nodeKey;
				ruleConsequent.nodes.get(i).add(childNode);
			}
		}

		return new CFGRule(rootId, ruleConsequent);
	}

	public BlockedPosteriorComputer getPosteriorComputer() {
		return samplePosteriorComputer;
	}

	public final CFGPrior getPrior() {
		return prior;
	}

	public void lockSamplerData() {
		prior.lockPrior();
		burninPosteriorComputer.getPrior().cfg = samplePosteriorComputer
				.getPrior().cfg;
	}

	@Override
	public void sampleAllTreesOnce(final int currentIteration,
			final int totalIterations, final AtomicBoolean stop) {
		final Thread termSignalHandler = new Thread() {
			@Override
			public void run() {
				stop.set(true);
			}
		};

		final List<TreeNode<TSGNode>> allTrees = Lists.newArrayList(treeCorpus);
		Collections.shuffle(allTrees);
		for (final TreeNode<TSGNode> tree : allTrees) {
			if (stop.get()) {
				break;
			}
			sampleSubTree(tree);
		}

		try {
			Runtime.getRuntime().removeShutdownHook(termSignalHandler);
		} catch (final Throwable e) {
			// Nothing here. It happens almost surely on interruption.
		}
	}

	public void sampleAt(final TreeNode<TSGNode> node) {
		checkNotNull(node);
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

	/**
	 * Compute a vector of doubles containing the unnormalized log2
	 * probabilities for each value of m (i.e. the number of nodes that will be
	 * split). If needed this can be normalized by subtracting
	 * sum(log2(upperCount+a+i),i,0,numOfSameTypeSites)
	 *
	 * @param upper
	 *            the upper tree
	 * @param lower
	 *            the lower tree
	 * @param joined
	 *            the joined tree
	 * @param numOfSameTypeSites
	 *            the number of sites that have the same type
	 * @param nSplit
	 *            the number of sites that have the upper/lower tree split
	 * @return
	 */
	public double[] sampleM(final TreeNode<TSGNode> upper,
			final TreeNode<TSGNode> lower, final TreeNode<TSGNode> joined,
			final int numOfSameTypeSites, final int nSplit) {
		final int nJoined = numOfSameTypeSites - nSplit;
		final boolean upperIsSameTypeAsLower = upper.getData().nodeKey == lower
				.getData().nodeKey;
		final boolean upperIsSameAsLower = upperIsSameTypeAsLower
				&& TSGNode.treesMatchToRoot(upper, lower);
		checkArgument(nJoined >= 0);
		final double upperPriorLog2Prob = samplePosteriorComputer
				.getLog2PriorForTree(upper);

		// Speedup
		final double lowerPriorLog2Prob;
		if (upperIsSameAsLower) {
			lowerPriorLog2Prob = upperPriorLog2Prob;
		} else {
			lowerPriorLog2Prob = samplePosteriorComputer
					.getLog2PriorForTree(lower);
		}

		final double joinedPriorLog2Prob = samplePosteriorComputer
				.getLog2PriorForTree(joined);

		// The counts of the TSG rules after removing these trees.
		final long upperCount = sampleGrammar.countTreeOccurences(upper)
				- nSplit - (upperIsSameAsLower ? nSplit : 0L);
		final long lowerCount = sampleGrammar.countTreeOccurences(lower)
				- nSplit - (upperIsSameAsLower ? nSplit : 0L);
		final long lowerRootCount = sampleGrammar.countTreesWithRoot(lower
				.getData())
				- nSplit
				- (upperIsSameTypeAsLower ? numOfSameTypeSites : 0L);
		final long jointCount = sampleGrammar.countTreeOccurences(joined)
				- nJoined;
		final long topRootCount = sampleGrammar.countTreesWithRoot(upper
				.getData())
				- numOfSameTypeSites
				- (upperIsSameTypeAsLower ? nSplit : 0L);

		checkArgument(lowerRootCount >= 0);
		checkArgument(topRootCount >= 0);
		checkArgument(lowerCount >= 0);
		checkArgument(upperCount >= 0);
		checkArgument(jointCount >= 0);

		final double log2a = DoubleMath
				.log2(samplePosteriorComputer.concentrationParameter);

		// Precompute split tree,first
		final double[] splitLog2Probs = new double[numOfSameTypeSites];

		for (int i = 0; i < numOfSameTypeSites; i++) {
			final int toAddNominator = i + (upperIsSameAsLower ? i : 0);
			final int toAddDenominator = i + (upperIsSameTypeAsLower ? i : 0);
			final double upperLog2prob = StatsUtil.log2SumOfExponentials(
					DoubleMath.log2(upperCount + toAddNominator), log2a
							+ upperPriorLog2Prob)
					- StatsUtil.log2SumOfExponentials(
							DoubleMath.log2(topRootCount + toAddDenominator),
							log2a);

			final double lowerLog2prob = StatsUtil.log2SumOfExponentials(
					DoubleMath.log2(lowerCount + toAddNominator), log2a
							+ lowerPriorLog2Prob)
					- StatsUtil.log2SumOfExponentials(
							DoubleMath.log2(lowerRootCount + toAddDenominator),
							log2a);

			splitLog2Probs[i] = upperLog2prob + lowerLog2prob;
		}

		final double[] mLogProbs = new double[numOfSameTypeSites + 1];
		for (int m = 0; m <= numOfSameTypeSites; m++) {

			double log2Prob;
			try {
				log2Prob = combinationCache
						.get(new BinomialCoefficientsParameters(
								numOfSameTypeSites, m));
			} catch (final ExecutionException e) {
				LOGGER.warning("Binomial Coefficient Cache failed "
						+ ExceptionUtils.getFullStackTrace(e));
				log2Prob = ArithmeticUtils.binomialCoefficientLog(
						numOfSameTypeSites, m) / LN_2;
			}

			// m split trees
			for (int i = 0; i < m; i++) {
				log2Prob += splitLog2Probs[i];
			}

			// Pick n-m joined
			final int jointTreesToAdd = numOfSameTypeSites - m;
			for (int i = 0; i < jointTreesToAdd; i++) {
				log2Prob += StatsUtil.log2SumOfExponentials(
						DoubleMath.log2(jointCount + i), log2a
								+ joinedPriorLog2Prob)
						- StatsUtil.log2SumOfExponentials(
								DoubleMath.log2(topRootCount + i + m
										+ (upperIsSameTypeAsLower ? m : 0)),
								log2a);
			}

			mLogProbs[m] = log2Prob;
		}

		return mLogProbs;
	}

	/**
	 * Perform TSG sampling on a single (full) tree. Sample each node in the
	 * tree one-by-one at random order
	 *
	 * @param tree
	 */
	private void sampleSubTree(final TreeNode<TSGNode> tree) {
		// A list of all the nodes.
		final List<TreeNode<TSGNode>> allNodes = Lists.newArrayList();

		// Collect all nodes (except from leaves and root)
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
					allNodes.add(child);
				}
			}

		}

		// Start the sampling
		Collections.shuffle(allNodes);
		for (final TreeNode<TSGNode> node : allNodes) {
			// ...and do the sampling
			try {
				sampleAt(node);
			} catch (final Throwable e) {
				LOGGER.severe("Failed at point sampling: "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}
	}

}

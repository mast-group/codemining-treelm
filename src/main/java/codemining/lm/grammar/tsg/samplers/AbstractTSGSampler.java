package codemining.lm.grammar.tsg.samplers;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.util.SettingsLoader;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.BoundType;
import com.google.common.collect.SortedMultiset;
import com.google.common.util.concurrent.AtomicDouble;

public abstract class AbstractTSGSampler implements Serializable {

	public static class PointSampleStats {

		public double joinProbability = Double.NaN;
		public int joinCount = -1;
		public int splitUpCount = -1;
		public int splitDownCount = -1;
		public int thisNodeCount = -1;
		public int thisRootCount = -1;
		public double cfgPriorProbJoin = Double.NaN;
		public double cfgPriorProbSplitUp = Double.NaN;
		public double cfgPriorProbSplitDown = Double.NaN;

		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("Join Prob:");
			sb.append(String.format("%.2E", joinProbability));
			sb.append(" join=");
			sb.append(joinCount + "/" + thisRootCount + " ");
			sb.append("split=" + splitUpCount + "/" + thisRootCount + "*"
					+ splitDownCount + "/" + thisNodeCount);
			sb.append(" prior join:" + String.format("%.1E", cfgPriorProbJoin));
			sb.append(" prior split:"
					+ String.format("%.1E", cfgPriorProbSplitUp) + "*"
					+ String.format("%.1E", cfgPriorProbSplitDown));
			return sb.toString();
		}
	}

	public static class SampleStats {
		final double log2Prob;
		final int totalNodes;

		public SampleStats(final double logProb, final int totalNodes) {
			this.log2Prob = logProb;
			this.totalNodes = totalNodes;
		}

		@Override
		public String toString() {
			return "Log-Prob: " + String.format("%.2f", log2Prob) + " ("
					+ totalNodes + " nodes with avg log-prob "
					+ String.format("%.3f", log2Prob / totalNodes) + ")";
		}

	}

	private static final long serialVersionUID = -4665068393040457778L;

	static final Logger LOGGER = Logger.getLogger(AbstractTSGSampler.class
			.getName());

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
	/**
	 * The grammar being mined. This represents the current sample of the
	 * grammar.
	 */
	protected final JavaFormattedTSGrammar sampleGrammar;
	/**
	 * The grammar after summing across the MCMC iteration (after burn-in).
	 */
	protected final JavaFormattedTSGrammar burninGrammar;
	/**
	 * The tree corpus where we mine TSGs from.
	 */
	protected List<TreeNode<TSGNode>> treeCorpus = new ArrayList<TreeNode<TSGNode>>();

	public AbstractTSGSampler(final JavaFormattedTSGrammar sampleGrammar,
			final JavaFormattedTSGrammar allSamplesGrammar) {
		checkArgument(sampleGrammar.getTreeExtractor() == allSamplesGrammar
				.getTreeExtractor());
		this.sampleGrammar = sampleGrammar;
		this.burninGrammar = allSamplesGrammar;
	}

	/**
	 * Add a single tree to be sampled.
	 * 
	 * @param tree
	 */
	public void addTree(final TreeNode<TSGNode> tree) {
		addTree(tree, false);
	}

	/**
	 * Add a single tree to the sampler. The sampler may decide to use the tree
	 * later, unless forceAdd is set to True.
	 * 
	 * @param tree
	 * @param forceAdd
	 * @return the final node as added (possibly the same as tree)
	 */
	public abstract TreeNode<TSGNode> addTree(final TreeNode<TSGNode> tree,
			boolean forceAdd);

	/**
	 * Calculate the log-probability of the whole corpus, given the TSG.
	 * 
	 * @return
	 */
	protected SampleStats calculateCorpusLogProb() {
		final AtomicDouble logProbSum = new AtomicDouble(0.);
		final AtomicInteger nNodes = new AtomicInteger(0);
		final ParallelThreadPool ptp = new ParallelThreadPool();
		for (final TreeNode<TSGNode> tree : treeCorpus) {
			ptp.pushTask(new Runnable() {

				@Override
				public void run() {
					logProbSum
							.addAndGet(computePosteriorLog2ProbabilityForTree(tree));
					nNodes.addAndGet(TreeNode.getTreeSize(tree));
				}

			});

		}
		ptp.waitForTermination();
		return new SampleStats(logProbSum.get(), nNodes.get());
	}

	public double computePosteriorLog2ProbabilityForTree(TreeNode<TSGNode> tree) {
		double log2Prob = 0;
		for (final TreeNode<TSGNode> rule : TSGNode.getAllRootsOf(tree)) {
			log2Prob += sampleGrammar.computeRulePosteriorLog2Probability(rule);
		}

		return log2Prob;
	}

	/**
	 * Get the grammar from all samples after burn-in.
	 * 
	 * @return
	 */
	public JavaFormattedTSGrammar getBurnInGrammar() {
		return burninGrammar;
	}

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
	 * Gibbs sampling the TSG n times. This function registers an
	 * 
	 * @param iterations
	 * @return the iteration that the sampling has stopped at or iterations+1 if
	 *         all iterations have been performed.
	 */
	public int performSampling(final int iterations) {
		final AtomicBoolean stop = new AtomicBoolean(false);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				stop.set(true);
			}

		});
		burninGrammar.clear();

		int currentIteration = 0;
		for (currentIteration = 0; currentIteration < iterations; currentIteration++) {
			System.out.println("=======Iteration " + currentIteration
					+ "==============");
			sampleAllTreesOnce(currentIteration, iterations, stop);
			if (CALC_LOGPROB && currentIteration % CALC_INTERVAL == 0) {
				System.out.println(calculateCorpusLogProb());
			}
			if (CALC_STATS && currentIteration % CALC_INTERVAL == 0) {
				printStats();
			}

			// Now add everything to sample, if burn-in has passed
			if (currentIteration > BURN_IN_PCT * iterations) {
				burninGrammar.addAll(sampleGrammar);
			}
			if (stop.get()) {
				LOGGER.info("Sampling interrupted.");
				break;
			}
		}

		return currentIteration;
	}

	/**
	 * Print statistics on stdout.
	 */
	protected void printStats() {
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

	public abstract void sampleAllTreesOnce(final int currentIteration,
			final int totalIterations, final AtomicBoolean stop);

}
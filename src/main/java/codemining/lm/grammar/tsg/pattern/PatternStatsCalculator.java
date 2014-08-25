package codemining.lm.grammar.tsg.pattern;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.java.tokenizers.JavaTokenizer;
import codemining.lm.grammar.java.ast.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tree.TreeNode.NodeDataPair;
import codemining.lm.grammar.tsg.FormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.base.Predicate;
import com.google.common.collect.BiMap;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.TreeMultiset;

/**
 * Class that calculate the pattern statistics for a tsg and a corpus.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 */
public class PatternStatsCalculator {

	/**
	 * Struct class for pattern statistics.
	 */
	public static class FilePatternStats {
		double coverage = Double.NaN;
		double fileRecall = Double.NaN;
		double avgPatternSize = Double.NaN;
		public int nUniqueMatched = 0;
		public int nNodesMatched = 0;
		public int nSitesMatched = 0;
		public int nPatternMatchedSizeSum = 0;
	}

	/**
	 * Return a multiset of rules that have at least minCount
	 *
	 * @param patterns
	 * @param minCount
	 * @return
	 */
	private static Multiset<Integer> getRulesWithMinCount(
			final Multiset<Integer> patterns, final int minCount) {
		final Multiset<Integer> prunedPatterns = HashMultiset.create();
		for (final Entry<Integer> pattern : patterns.entrySet()) {
			if (pattern.getCount() >= minCount) {
				prunedPatterns.add(pattern.getElement(), pattern.getCount());
			}
		}

		return prunedPatterns;
	}

	private static final Logger LOGGER = Logger
			.getLogger(PatternStatsCalculator.class.getName());

	/**
	 * A predicate for comparing integer tree nodes.
	 */
	public static final Predicate<NodeDataPair<Integer>> BASE_EQUALITY_COMPARATOR = new Predicate<NodeDataPair<Integer>>() {

		@Override
		public boolean apply(final NodeDataPair<Integer> nodePair) {
			return nodePair.fromNode.equals(nodePair.toNode);
		}

	};

	/**
	 * The tree format used to extract trees.
	 */
	protected final AbstractJavaTreeExtractor treeFormat;

	/**
	 * The patterns to be sought for.
	 */
	final Multiset<TreeNode<Integer>> patterns;

	/**
	 * A cache containing the sizes of the trees in the grammar (this is an
	 * identity map).
	 */
	final Map<Integer, Integer> patternSizes = Maps.newHashMap();

	/**
	 * The file sizes in number of nodes.
	 */
	final Map<File, Integer> fileSizes;

	private final Collection<File> allFiles;

	/**
	 * A table containing File,MatchedPatterns=>IdentitySet of matched nodes
	 */
	private final Table<File, Integer, Integer> filePatterns;
	private final ReentrantLock filePatternsLock = new ReentrantLock();
	private final Table<File, Integer, Integer> filePatternsCount;
	private final ReentrantLock filePatternsCountLock = new ReentrantLock();
	private final BiMap<TreeNode<Integer>, Integer> patternDictionary = HashBiMap
			.create();

	public PatternStatsCalculator(final AbstractJavaTreeExtractor treeFormat,
			final FormattedTSGrammar grammar, final File directory) {
		this.treeFormat = treeFormat;
		patterns = HashMultiset.create();
		int currentIdx = 0;
		for (final Multiset<TreeNode<TSGNode>> production : grammar
				.getInternalGrammar().values()) {
			for (final Multiset.Entry<TreeNode<TSGNode>> rule : production
					.entrySet()) {
				final TreeNode<Integer> intTree = TSGNode.tsgTreeToInt(rule
						.getElement());
				patterns.add(intTree, rule.getCount());
				patternDictionary.put(intTree, currentIdx);
				patternSizes.put(currentIdx, intTree.getTreeSize());
				currentIdx++;
			}
		}
		allFiles = FileUtils
				.listFiles(directory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);
		fileSizes = new MapMaker()
		.concurrencyLevel(ParallelThreadPool.NUM_THREADS)
		.initialCapacity(allFiles.size()).makeMap();
		filePatterns = HashBasedTable.create(allFiles.size(),
				patterns.size() / 10);
		filePatternsCount = HashBasedTable.create(allFiles.size(),
				patterns.size() / 1);
	}

	public PatternStatsCalculator(final AbstractJavaTreeExtractor treeFormat,
			final Set<TreeNode<Integer>> patterns, final File directory) {
		this.treeFormat = treeFormat;
		this.patterns = HashMultiset.create(patterns);
		int currentIdx = 0;
		for (final Multiset.Entry<TreeNode<Integer>> rule : this.patterns
				.entrySet()) {
			patternDictionary.put(rule.getElement(), currentIdx);
			patternSizes.put(currentIdx, rule.getElement().getTreeSize());
			currentIdx++;
		}

		allFiles = FileUtils
				.listFiles(directory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);

		fileSizes = new MapMaker()
				.concurrencyLevel(ParallelThreadPool.NUM_THREADS)
				.initialCapacity(allFiles.size()).makeMap();
		filePatterns = HashBasedTable.create(allFiles.size(),
				patterns.size() / 10);
		filePatternsCount = HashBasedTable.create(allFiles.size(),
				patterns.size() / 1);
	}

	/**
	 * Return the list of patterns a specific tree.
	 */
	private void completePatternsTable(final File f,
			final TreeNode<Integer> fileTree) {
		for (final TreeNode<Integer> pattern : patterns.elementSet()) {
			final int patternId = patternDictionary.get(pattern);
			final Set<TreeNode<Integer>> overlappingNodes = Sets
					.newIdentityHashSet();
			int count = 0;

			final ArrayDeque<TreeNode<Integer>> toLook = new ArrayDeque<TreeNode<Integer>>();
			toLook.push(fileTree);

			// Do a pre-order visit
			while (!toLook.isEmpty()) {
				final TreeNode<Integer> currentNode = toLook.pop();
				// at each node check if we have a partial match with the
				// current patterns

				if (pattern.partialMatch(currentNode, BASE_EQUALITY_COMPARATOR,
						false)) {
					overlappingNodes.addAll(currentNode
							.getOverlappingNodesWith(pattern));
					count++;
				}
				// Proceed visiting
				for (final List<TreeNode<Integer>> childProperties : currentNode
						.getChildrenByProperty()) {
					for (final TreeNode<Integer> child : childProperties) {
						toLook.push(child);
					}
				}
			}

			filePatternsCountLock.lock();
			try {
				filePatternsCount.put(f, patternId, count);
			} finally {
				filePatternsCountLock.unlock();
			}

			filePatternsLock.lock();
			try {
				filePatterns.put(f, patternId, overlappingNodes.size());
			} finally {
				filePatternsLock.unlock();
			}

		}
	}

	/**
	 * Return a multiset without the rules that are of larger than minSize
	 *
	 * @param prunedByCountBySize
	 * @param i
	 * @return
	 */
	private Multiset<Integer> getRulesWithMinSize(
			final Multiset<Integer> prunedByCountBySize, final int minSize) {
		final Multiset<Integer> prunedPatterns = HashMultiset.create();
		for (final Entry<Integer> patternId : prunedByCountBySize.entrySet()) {
			if (patternSizes.get(patternId.getElement()) < minSize) {
				continue;
			}
			prunedPatterns.add(patternId.getElement(), patternId.getCount());
		}
		return prunedPatterns;
	}

	/**
	 * Return a map of all the patterns that can be found in a single file.
	 */
	private void loadPatternsForFiles() {
		final ParallelThreadPool ptp = new ParallelThreadPool();
		for (final File f : allFiles) {
			ptp.pushTask(new Runnable() {
				@Override
				public void run() {
					try {
						final TreeNode<Integer> tree = treeFormat.getTree(f);
						fileSizes.put(f, tree.getTreeSize());
						completePatternsTable(f, tree);
					} catch (final IOException e) {
						LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
					}
				}
			});
		}
		ptp.waitForTermination();
	}

	/**
	 *
	 * @param prunedByCountBySize
	 *            the pattern ids.
	 * @param minCount
	 * @param minSize
	 */
	private void printPatternStatistics(
			final Multiset<Integer> prunedByCountBySize, final int minCount,
			final int minSize) {

		final Multiset<Integer> seenPatterns = ConcurrentHashMultiset.create();
		final Map<File, FilePatternStats> allFilePatternStats = new MapMaker()
				.concurrencyLevel(ParallelThreadPool.NUM_THREADS)
				.initialCapacity(filePatterns.rowKeySet().size()).makeMap();

		final ParallelThreadPool ptp = new ParallelThreadPool();

		for (final File file : filePatterns.rowKeySet()) {
			ptp.pushTask(new Runnable() {

				@Override
				public void run() {
					final Set<Integer> patternsInFile = filePatterns.row(file)
							.keySet();
					final Set<Integer> matchedPatterns = Sets.intersection(
							patternsInFile, prunedByCountBySize.elementSet());
					final FilePatternStats stats = new FilePatternStats();
					allFilePatternStats.put(file, stats);
					stats.nUniqueMatched = matchedPatterns.size();
					for (final int patternId : matchedPatterns) {
						final int patternSize = patternSizes.get(patternId);
						final int nTimesPatternInFile = filePatternsCount.get(
								file, patternId);

						stats.nNodesMatched += filePatterns
								.get(file, patternId);
						stats.nSitesMatched += nTimesPatternInFile;
						stats.nPatternMatchedSizeSum += patternSize;
						seenPatterns.add(patternId, nTimesPatternInFile);
					}

					stats.coverage = ((double) stats.nNodesMatched)
							/ fileSizes.get(file);
					stats.fileRecall = ((double) stats.nUniqueMatched)
							/ prunedByCountBySize.elementSet().size();
					stats.avgPatternSize = ((double) stats.nNodesMatched)
							/ stats.nUniqueMatched;
				}
			});

		}

		ptp.waitForTermination();

		// Compute test corpus stats
		double avgCoverage = 0;
		double avgFileRecall = 0;
		double avgSitesMatched = 0;
		double avgPatternSizePerFile = 0;
		for (final FilePatternStats stats : allFilePatternStats.values()) {
			avgCoverage += stats.coverage;
			avgFileRecall += stats.fileRecall;
			avgSitesMatched += stats.nSitesMatched;
			if (!Double.isNaN(stats.avgPatternSize)) {
				avgPatternSizePerFile += stats.avgPatternSize;
			}
		}
		avgCoverage /= allFilePatternStats.size();
		avgFileRecall /= allFilePatternStats.size();
		avgSitesMatched /= allFilePatternStats.size();
		avgPatternSizePerFile /= allFilePatternStats.size();

		// Compute pattern corpus stats
		final int nSeenPatterns = seenPatterns.elementSet().size();
		final double patternRecall = ((double) seenPatterns.elementSet().size())
				/ prunedByCountBySize.elementSet().size();
		double avgPatternSizeSeen = 0;
		for (final Entry<Integer> patternId : seenPatterns.entrySet()) {
			avgPatternSizeSeen += patternId.getCount()
					* checkNotNull(patternSizes.get(patternId.getElement()));
		}
		avgPatternSizeSeen /= seenPatterns.size();

		System.out.println(minCount + "," + minSize + "," + nSeenPatterns + ","
				+ String.format("%.4f", patternRecall) + ","
				+ String.format("%.4f", avgPatternSizeSeen) + ","
				+ String.format("%.4f", avgCoverage) + ","
				+ String.format("%.4f", avgFileRecall) + ","
				+ String.format("%.4f", avgSitesMatched) + ","
				+ String.format("%.4f", avgPatternSizePerFile));
	}

	public void printStatisticsFor(final int[] minPatternSizes,
			final int[] minPatternCounts) {
		Arrays.sort(minPatternCounts);
		Arrays.sort(minPatternSizes);

		loadPatternsForFiles();

		Multiset<Integer> prunedByCount = TreeMultiset.create();
		for (final Entry<TreeNode<Integer>> pattern : patterns.entrySet()) {
			prunedByCount.add(patternDictionary.get(pattern.getElement()),
					pattern.getCount());
		}
		System.out
				.println("minCount,minSize,nSeenPatterns,patternRecall,avgPatternSizeSeen,avgCoverage,avgFileRecall,avgSitesMatched,avgPatternSizePerFile");

		for (int i = 0; i < minPatternCounts.length; i++) {
			prunedByCount = getRulesWithMinCount(prunedByCount,
					minPatternCounts[i]);

			Multiset<Integer> prunedByCountBySize = prunedByCount;
			for (int j = 0; j < minPatternSizes.length; j++) {
				prunedByCountBySize = getRulesWithMinSize(prunedByCountBySize,
						minPatternSizes[j]);
				// Great now our patterns are in prunedByCountBySize
				printPatternStatistics(prunedByCountBySize,
						minPatternCounts[i], minPatternSizes[j]);
			}
		}
	}
}
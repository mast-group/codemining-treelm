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
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.java.codeutils.JavaTokenizer;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tree.TreeNode.NodeDataPair;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;

import com.google.common.base.Predicate;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Sets;

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

	private static final Logger LOGGER = Logger
			.getLogger(PatternStatsCalculator.class.getName());

	/**
	 * Return the list of patterns a specific tree.
	 */
	public static Multiset<TreeNode<Integer>> getPatternsForTree(
			final TreeNode<Integer> tree, final Set<TreeNode<Integer>> patterns) {
		final Multiset<TreeNode<Integer>> treePatterns = HashMultiset.create();
		final ArrayDeque<TreeNode<Integer>> toLook = new ArrayDeque<TreeNode<Integer>>();
		toLook.push(tree);

		// Do a pre-order visit
		while (!toLook.isEmpty()) {
			final TreeNode<Integer> currentNode = toLook.pop();
			// at each node check if we have a partial match with any of the
			// patterns
			for (final TreeNode<Integer> pattern : patterns) {
				if (pattern.partialMatch(currentNode, BASE_EQUALITY_COMPARATOR,
						false)) {
					treePatterns.add(pattern);
				}
			}

			// Proceed visiting
			for (final List<TreeNode<Integer>> childProperties : currentNode
					.getChildrenByProperty()) {
				for (final TreeNode<Integer> child : childProperties) {
					toLook.push(child);
				}
			}

		}
		return treePatterns;
	}

	/**
	 * Return a multiset of rules that have at least minCount
	 * 
	 * @param patterns
	 * @param minCount
	 * @return
	 */
	private static Multiset<TreeNode<Integer>> getRulesWithMinCount(
			final Multiset<TreeNode<Integer>> patterns, final int minCount) {
		final Multiset<TreeNode<Integer>> prunedPatterns = HashMultiset
				.create();
		for (final Entry<TreeNode<Integer>> pattern : patterns.entrySet()) {
			if (pattern.getCount() >= minCount) {
				prunedPatterns.add(pattern.getElement(), pattern.getCount());
			}
		}

		return prunedPatterns;
	}

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
	final Map<TreeNode<Integer>, Integer> patternSizes;

	/**
	 * The file sizes in number of nodes.
	 */
	final Map<File, Integer> fileSizes;

	/**
	 * A predicate for comparing integer tree nodes.
	 */
	public static final Predicate<NodeDataPair<Integer>> BASE_EQUALITY_COMPARATOR = new Predicate<NodeDataPair<Integer>>() {

		@Override
		public boolean apply(final NodeDataPair<Integer> nodePair) {
			return nodePair.fromNode.equals(nodePair.toNode);
		}

	};

	private final Collection<File> allFiles;

	private final Map<File, Multiset<TreeNode<Integer>>> filePatterns;

	public PatternStatsCalculator(final AbstractJavaTreeExtractor treeFormat,
			final JavaFormattedTSGrammar grammar, final File directory) {
		this.treeFormat = treeFormat;
		patterns = HashMultiset.create();
		for (final Multiset<TreeNode<TSGNode>> production : grammar
				.getInternalGrammar().values()) {
			for (final com.google.common.collect.Multiset.Entry<TreeNode<TSGNode>> rule : production
					.entrySet()) {
				patterns.add(TSGNode.tsgTreeToInt(rule.getElement()),
						rule.getCount());
			}
		}

		patternSizes = Maps.newHashMap();
		for (final TreeNode<Integer> pattern : patterns.elementSet()) {
			patternSizes.put(pattern, pattern.getTreeSize());
		}

		allFiles = FileUtils
				.listFiles(directory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);

		fileSizes = Maps.newHashMap();
		filePatterns = Maps.newHashMap();
	}

	/**
	 * Return a multiset without the rules that are of larger than minSize
	 * 
	 * @param prunedByCountBySize
	 * @param i
	 * @return
	 */
	private Multiset<TreeNode<Integer>> getRulesWithMinSize(
			final Multiset<TreeNode<Integer>> prunedByCountBySize,
			final int minSize) {
		final Multiset<TreeNode<Integer>> prunedPatterns = HashMultiset
				.create();
		for (final Entry<TreeNode<Integer>> pattern : prunedByCountBySize
				.entrySet()) {
			if (checkNotNull(patternSizes.get(pattern.getElement())) < minSize) {
				continue;
			}
			prunedPatterns.add(pattern.getElement(), pattern.getCount());
		}
		return prunedPatterns;
	}

	/**
	 * Return a map of all the patterns that can be found in a single file.
	 */
	private void loadPatternsForFiles(final Multiset<TreeNode<Integer>> patterns) {
		for (final File f : allFiles) {
			try {
				final TreeNode<Integer> tree = treeFormat.getTree(f);
				fileSizes.put(f, tree.getTreeSize());
				final Multiset<TreeNode<Integer>> patternsForFile = getPatternsForTree(
						tree, patterns.elementSet());
				filePatterns.put(f, patternsForFile);
			} catch (final IOException e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}
	}

	private void printPatternStatistics(
			final Map<File, Multiset<TreeNode<Integer>>> filesPatterns,
			final Multiset<TreeNode<Integer>> prunedByCountBySize,
			final int minCount, final int minSize) {

		final Multiset<TreeNode<Integer>> seenPatterns = HashMultiset.create();
		final List<FilePatternStats> allFilePatternStats = Lists.newArrayList();

		for (final java.util.Map.Entry<File, Multiset<TreeNode<Integer>>> filePatterns : filesPatterns
				.entrySet()) {
			final Set<TreeNode<Integer>> patternsInFile = filePatterns
					.getValue().elementSet();
			final Set<TreeNode<Integer>> matchedPatterns = Sets.intersection(
					patternsInFile, prunedByCountBySize.elementSet());
			final FilePatternStats stats = new FilePatternStats();
			allFilePatternStats.add(stats);
			stats.nUniqueMatched = matchedPatterns.size();
			for (final TreeNode<Integer> pattern : matchedPatterns) {
				final int patternSize = patternSizes.get(pattern);
				final int nTimesPatternInFile = filePatterns.getValue().count(
						pattern);

				stats.nNodesMatched += patternSize * nTimesPatternInFile;
				stats.nSitesMatched += nTimesPatternInFile;
				stats.nPatternMatchedSizeSum += patternSize;
				seenPatterns.add(pattern, nTimesPatternInFile);
			}

			stats.coverage = ((double) stats.nNodesMatched)
					/ fileSizes.get(filePatterns.getKey());
			stats.fileRecall = ((double) stats.nUniqueMatched)
					/ prunedByCountBySize.elementSet().size();
			stats.avgPatternSize = ((double) stats.nNodesMatched)
					/ stats.nUniqueMatched;
		}

		// Compute test corpus stats
		double avgCoverage = 0;
		double avgFileRecall = 0;
		double avgSitesMatched = 0;
		double avgPatternSizePerFile = 0;
		for (final FilePatternStats stats : allFilePatternStats) {
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
		for (final Entry<TreeNode<Integer>> pattern : seenPatterns.entrySet()) {
			avgPatternSizeSeen += pattern.getCount()
					* checkNotNull(patternSizes.get(pattern.getElement()));
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

		loadPatternsForFiles(patterns);

		Multiset<TreeNode<Integer>> prunedByCount = patterns;
		System.out
				.println("minCount,minSize,nSeenPatterns,patternRecall,avgPatternSizeSeen,avgCoverage,avgFileRecall,avgSitesMatched,avgPatternSizePerFile");

		for (int i = 0; i < minPatternCounts.length; i++) {
			prunedByCount = getRulesWithMinCount(prunedByCount,
					minPatternCounts[i]);

			Multiset<TreeNode<Integer>> prunedByCountBySize = prunedByCount;
			for (int j = 0; j < minPatternSizes.length; j++) {
				prunedByCountBySize = getRulesWithMinSize(prunedByCountBySize,
						minPatternSizes[j]);
				// Great now our patterns are in prunedByCountBySize
				printPatternStatistics(filePatterns, prunedByCountBySize,
						minPatternCounts[i], minPatternSizes[j]);
			}
		}
	}
}
/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.java.codeutils.JavaTokenizer;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.pattern.tui.PatternCooccurence.LikelihoodRatio;
import codemining.util.data.UnorderedPair;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;

/**
 * Evaluate the cooccuring elements recall.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class CooccuringPatternPrediction {
	/**
	 * Struct to contain statistics.
	 * 
	 */
	public static final class OccurenceStats {
		private int appearancesOfAtLeastElement = 0;
		private int appearancesOfBothElements = 0;

		public OccurenceStats() {
		}

		/**
		 * Aggregate multiple statistics to a single statistic.
		 * 
		 * @param collection
		 */
		public OccurenceStats(final Collection<OccurenceStats> collection) {
			for (final OccurenceStats stat : collection) {
				appearancesOfAtLeastElement += stat.appearancesOfAtLeastElement;
				appearancesOfBothElements += stat.appearancesOfBothElements;
			}
		}

		public void seenBoth() {
			appearancesOfAtLeastElement++;
			appearancesOfBothElements++;
		}

		public void seenOnlyOne() {
			appearancesOfAtLeastElement++;
		}

		@Override
		public String toString() {
			final double pctAcc = ((double) appearancesOfBothElements)
					/ appearancesOfAtLeastElement;
			return String.format("%.2f", pctAcc);
		}

	}

	private static final Logger LOGGER = Logger
			.getLogger(CooccuringPatternPrediction.class.getName());

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 5) {
			System.err
					.println("Usage <tsg> <minPatternCount> <minPatternSize> <trainPath> <testPath>");
			System.exit(-1);
		}

		final JavaFormattedTSGrammar grammar = (JavaFormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final AbstractJavaTreeExtractor format = grammar.getJavaTreeExtractor();

		final int minPatternCount = Integer.parseInt(args[1]);
		final int minPatternSize = Integer.parseInt(args[2]);
		final Set<TreeNode<Integer>> patterns = PatternsInCorpus.getPatterns(
				grammar, minPatternCount, minPatternSize);

		final PatternCooccurence cooccurenceData = new PatternCooccurence();

		final File trainDirectory = new File(args[3]);
		final Collection<File> trainFiles = FileUtils
				.listFiles(trainDirectory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);
		for (final File f : trainFiles) {
			try {
				final TreeNode<Integer> fileAst = format.getTree(f);
				cooccurenceData.add(PatternsInCorpus.getPatternsForTree(
						fileAst, patterns).elementSet());
			} catch (final Exception e) {
				LOGGER.warning("Error in file " + f + " "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}

		LOGGER.info("Patterns Loaded, building co-appearing sets...");
		// Create co-occuring set
		final SortedSet<LikelihoodRatio> likelyCoappearingElements = cooccurenceData
				.likelyCoappearingElements(10);
		LOGGER.info("Patterns Built, filtering...");
		PatternCooccurence.filterCoappearingPatterns(likelyCoappearingElements);

		printPatterns(likelyCoappearingElements, format);

		// Test

		final Map<UnorderedPair<TreeNode<Integer>>, OccurenceStats> pairStats = Maps
				.newHashMap();
		final Multimap<TreeNode<Integer>, TreeNode<Integer>> reverseFrequentPatternMap = HashMultimap
				.create();

		for (final LikelihoodRatio lr : likelyCoappearingElements) {
			pairStats.put(lr.pair, new OccurenceStats());
			reverseFrequentPatternMap.put(lr.pair.first, lr.pair.second);
			reverseFrequentPatternMap.put(lr.pair.second, lr.pair.first);
		}

		final File testDirectory = new File(args[4]);
		final Collection<File> testFiles = FileUtils
				.listFiles(testDirectory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);
		for (final File f : testFiles) {
			try {
				final TreeNode<Integer> fileAst = format.getTree(f);
				final Multiset<TreeNode<Integer>> testFilePatterns = PatternsInCorpus
						.getPatternsForTree(fileAst, patterns);
				for (final TreeNode<Integer> pattern : testFilePatterns) {
					for (final TreeNode<Integer> frequentlyCooccuringPattern : reverseFrequentPatternMap
							.get(pattern)) {
						final OccurenceStats stats = pairStats
								.get(UnorderedPair.createUnordered(pattern,
										frequentlyCooccuringPattern));
						if (testFilePatterns
								.contains(frequentlyCooccuringPattern)) {
							stats.seenBoth();
						} else {
							stats.seenOnlyOne();
						}
					}
				}
			} catch (final Exception e) {
				LOGGER.warning("Error in file " + f + " "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}

		// Now get accuracy and print
		final OccurenceStats aggregate = new OccurenceStats(pairStats.values());
		System.out.println("Co-occurent set accuracy " + aggregate);
	}

	private static void printPatterns(
			final SortedSet<LikelihoodRatio> likelyCoappearingElements,
			final AbstractJavaTreeExtractor format) {
		for (final LikelihoodRatio lr : likelyCoappearingElements) {
			System.out
					.println("----------------------------------------------");
			PrintPatternsFromTsg.printIntTree(format, lr.pair.first);
			System.out.println("and");
			PrintPatternsFromTsg.printIntTree(format, lr.pair.second);
			System.out.println("ll:"
					+ String.format("%.2f", lr.likelihoodRatio));
			System.out
					.println("----------------------------------------------");
		}

	}

}

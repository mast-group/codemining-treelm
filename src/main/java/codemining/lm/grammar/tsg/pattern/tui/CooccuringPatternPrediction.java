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

import com.google.common.base.Optional;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

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

	public static final int LIKELYHOOD_THRESHOLD = 10;

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

		final File trainDirectory = new File(args[3]);
		CooccuringPatternPrediction cpp = new CooccuringPatternPrediction(
				patterns);
		final SortedSet<LikelihoodRatio<Integer>> likelyCoappearingElements = cpp
				.loadData(format, trainDirectory);

		cpp.printPatterns(likelyCoappearingElements, format);

		// Test
		cpp.test(new File(args[4]), likelyCoappearingElements, format);
	}

	final PatternCooccurence<Integer> cooccurenceData = new PatternCooccurence<Integer>();

	private final BiMap<Integer, TreeNode<Integer>> patternDictionary = HashBiMap
			.create();

	public CooccuringPatternPrediction(final Set<TreeNode<Integer>> patterns) {
		int i = 0;
		for (final TreeNode<Integer> pattern : patterns) {
			patternDictionary.put(i, pattern);
			i++;
		}
	}

	/**
	 * Filter the set of co-appearing patterns to remove trees that imply each
	 * other.
	 * 
	 * @param patterns
	 */
	public void filterCoappearingPatterns(
			final SortedSet<LikelihoodRatio<Integer>> patterns) {
		final Set<LikelihoodRatio<Integer>> toBeRemoved = Sets
				.newIdentityHashSet();

		for (final LikelihoodRatio<Integer> lr : patterns) {
			final int tree1id = lr.pair.first;
			final int tree2id = lr.pair.second;
			final TreeNode<Integer> tree1 = patternDictionary.get(tree1id);
			final TreeNode<Integer> tree2 = patternDictionary.get(tree2id);
			final Optional<TreeNode<Integer>> common = tree1
					.getMaximalOverlappingTree(tree2);
			if (!common.isPresent()) {
				continue;
			}
			final TreeNode<Integer> commonTree = common.get();
			if (commonTree.equals(tree1) || commonTree.equals(tree2)) {
				toBeRemoved.add(lr);
			}
		}

		patterns.removeAll(toBeRemoved);
	}

	/**
	 * @param format
	 * @param patterns
	 * @param trainDirectory
	 * @return
	 */
	private SortedSet<LikelihoodRatio<Integer>> loadData(
			final AbstractJavaTreeExtractor format, final File trainDirectory) {
		final Collection<File> trainFiles = FileUtils
				.listFiles(trainDirectory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);
		for (final File f : trainFiles) {
			try {
				final TreeNode<Integer> fileAst = format.getTree(f);
				final Set<Integer> patternsIdsInFile = patternInFileId(fileAst);
				cooccurenceData.add(patternsIdsInFile);
			} catch (final Exception e) {
				LOGGER.warning("Error in file " + f + " "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}

		LOGGER.info("Patterns Loaded, building co-appearing sets...");
		// Create co-occuring set
		final SortedSet<LikelihoodRatio<Integer>> likelyCoappearingElements = cooccurenceData
				.likelyCoappearingElements(LIKELYHOOD_THRESHOLD);
		LOGGER.info("Patterns Built, filtering...");
		filterCoappearingPatterns(likelyCoappearingElements);
		return likelyCoappearingElements;
	}

	/**
	 * @param fileAst
	 * @return
	 */
	private Set<Integer> patternInFileId(final TreeNode<Integer> fileAst) {
		final Set<TreeNode<Integer>> patternsInFile = PatternsInCorpus
				.getPatternsForTree(fileAst, patternDictionary.values())
				.elementSet();

		final Set<Integer> patternsIdsInFile = Sets.newHashSet();
		for (final TreeNode<Integer> pattern : patternsInFile) {
			patternsIdsInFile.add(patternDictionary.inverse().get(pattern));
		}
		return patternsIdsInFile;
	}

	public void printPatterns(
			final SortedSet<LikelihoodRatio<Integer>> likelyCoappearingElements,
			final AbstractJavaTreeExtractor format) {
		for (final LikelihoodRatio<Integer> lr : likelyCoappearingElements) {
			System.out
					.println("----------------------------------------------");
			PrintPatternsFromTsg.printIntTree(format,
					patternDictionary.get(lr.pair.first));
			System.out.println("and");
			PrintPatternsFromTsg.printIntTree(format,
					patternDictionary.get(lr.pair.second));
			System.out.println("ll:"
					+ String.format("%.2f", lr.likelihoodRatio));
			System.out
					.println("----------------------------------------------");
		}

	}

	private void test(File testDirectory,
			SortedSet<LikelihoodRatio<Integer>> likelyCoappearingElements,
			final AbstractJavaTreeExtractor format) {
		final Map<UnorderedPair<Integer>, OccurenceStats> pairStats = Maps
				.newHashMap();
		final Multimap<Integer, Integer> reverseFrequentPatternMap = HashMultimap
				.create();

		for (final LikelihoodRatio<Integer> lr : likelyCoappearingElements) {
			pairStats.put(lr.pair, new OccurenceStats());
			reverseFrequentPatternMap.put(lr.pair.first, lr.pair.second);
			reverseFrequentPatternMap.put(lr.pair.second, lr.pair.first);
		}

		final Collection<File> testFiles = FileUtils
				.listFiles(testDirectory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);
		for (final File f : testFiles) {
			try {
				final TreeNode<Integer> fileAst = format.getTree(f);
				final Set<Integer> testFilePatterns = patternInFileId(fileAst);
				for (final int pattern : testFilePatterns) {
					for (final int frequentlyCooccuringPattern : reverseFrequentPatternMap
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
}

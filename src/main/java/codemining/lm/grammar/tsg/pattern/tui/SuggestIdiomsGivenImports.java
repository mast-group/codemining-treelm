/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jdt.core.dom.CompilationUnit;

import codemining.java.codedata.PackageInfoExtractor;
import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.codeutils.JavaTokenizer;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.pattern.tui.ElementCooccurence.ElementMutualInformation;
import codemining.util.SettingsLoader;
import codemining.util.parallel.ParallelThreadPool;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.base.Objects;
import com.google.common.collect.BoundType;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedMultiset;
import com.google.common.collect.TreeMultiset;

/**
 * Code to suggest idioms given the imports of a file.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class SuggestIdiomsGivenImports {

	public static class RecallStats {
		public final int[] RECALL_AT_RANK_K_VALUES = { 1, 2, 3, 4, 5, 10, 15,
				20 };
		public final double[] SUGGESTION_FREQUENCY_VALUES = { .01, .05, .1,
				.15, .2, .3, .4, .5, .6, .7, .8, .9, 1 };

		/**
		 * For each recall value get the decision values.
		 */
		final Map<Integer, SortedMultiset<Double>> suggestionsValuesAtRank = Maps
				.newTreeMap();

		final Map<Integer, SortedMultiset<Double>> correctSuggestionsValuesAtRank = Maps
				.newTreeMap();

		public RecallStats() {
			// Initialize
			for (int i = 0; i < RECALL_AT_RANK_K_VALUES.length; i++) {
				suggestionsValuesAtRank.put(RECALL_AT_RANK_K_VALUES[i],
						TreeMultiset.<Double> create());
				correctSuggestionsValuesAtRank.put(RECALL_AT_RANK_K_VALUES[i],
						TreeMultiset.<Double> create());
			}
		}

		/**
		 * Return the threshold for the given number of elements.
		 * 
		 * @param elements
		 * @param proportion
		 * @return
		 */
		private double getThreshold(final SortedMultiset<Double> elements,
				final int nElements) {
			checkArgument(elements.size() >= nElements);
			double threshold = elements.firstEntry().getElement();
			int nSeen = 0;
			for (final Entry<Double> elementEntry : elements.entrySet()) {
				if (nSeen > nElements) {
					break;
				}
				nSeen += elementEntry.getCount();
				threshold = elementEntry.getElement();
			}
			checkArgument(threshold <= elements.firstEntry().getElement(),
					"Threshold is %s but first element is %s", threshold,
					elements.firstEntry().getElement());
			return threshold;
		}

		public void printStats() {
			System.out.println(Arrays.toString(SUGGESTION_FREQUENCY_VALUES));
			for (final int k : RECALL_AT_RANK_K_VALUES) {
				System.out.print(k);
				final SortedMultiset<Double> suggestionValues = suggestionsValuesAtRank
						.get(k);
				final SortedMultiset<Double> suggestionCorrectValues = correctSuggestionsValuesAtRank
						.get(k);
				for (final double suggestionFrequency : SUGGESTION_FREQUENCY_VALUES) {

					final int nSuggestionsToBeMade = (int) Math
							.ceil(suggestionFrequency * suggestionValues.size());

					// Compute threshold
					final double threshold = getThreshold(
							suggestionValues.descendingMultiset(),
							nSuggestionsToBeMade);
					final int nCorrectSuggestionsMade = suggestionCorrectValues
							.tailMultiset(threshold, BoundType.CLOSED).size();
					final int nSuggestionsMade = suggestionValues.tailMultiset(
							threshold, BoundType.CLOSED).size();
					checkArgument(
							nCorrectSuggestionsMade <= nSuggestionsMade,
							"Made %s suggestions, out of which %s were correct",
							nSuggestionsMade, nCorrectSuggestionsMade);
					final double recall = ((double) nCorrectSuggestionsMade)
							/ nSuggestionsMade;
					System.out.print("," + String.format("%.4E", recall));
				}
				System.out.println();
			}
		}

		/**
		 * Push results.
		 * 
		 * @param realPatternIds
		 * @param suggestions
		 */
		public synchronized void pushResults(final Set<Integer> realPatternIds,
				final SortedSet<Suggestion> suggestions) {
			int currentK = 1;
			int currentRankIdx = 0;
			boolean foundPattern = false;
			double scoreFound = Double.NEGATIVE_INFINITY;
			for (final Suggestion suggestion : suggestions) {
				if (realPatternIds.contains(suggestion.id) && !foundPattern) {
					foundPattern = true;
					scoreFound = suggestion.score;
				}
				checkArgument(
						currentK <= RECALL_AT_RANK_K_VALUES[currentRankIdx],
						"CurrentK is %s but we still haven't evaluated idx %s",
						currentK, currentRankIdx);

				if (RECALL_AT_RANK_K_VALUES[currentRankIdx] == currentK) {
					// Push the results so far.
					if (foundPattern) {
						suggestionsValuesAtRank.get(currentK).add(scoreFound);
						correctSuggestionsValuesAtRank.get(currentK).add(
								scoreFound);
						checkArgument(suggestion.score <= scoreFound,
								"Score is %s but best is %s", suggestion.score,
								scoreFound);
					} else {
						suggestionsValuesAtRank.get(currentK).add(
								suggestion.score);
					}
					currentRankIdx++;
				}

				currentK++;
				if (currentRankIdx >= RECALL_AT_RANK_K_VALUES.length) {
					break;
				}
			}
		}

	}

	public static class Suggestion implements Comparable<Suggestion> {

		public final int id;

		public final double score;

		public Suggestion(final int id, final double score) {
			this.id = id;
			checkArgument(!Double.isNaN(score));
			this.score = score;
		}

		@Override
		public int compareTo(final Suggestion other) {
			return ComparisonChain.start().compare(other.score, score)
					.compare(id, other.id).result();
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
			final Suggestion other = (Suggestion) obj;
			if (id != other.id) {
				return false;
			}
			if (Double.doubleToLongBits(score) != Double
					.doubleToLongBits(other.score)) {
				return false;
			}
			return true;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(id, score);
		}

	}

	public static final boolean SHOW_SUGGESTIONS = SettingsLoader
			.getBooleanSetting("printSuggestions", false);

	private static final Logger LOGGER = Logger
			.getLogger(SuggestIdiomsGivenImports.class.getName());

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 2) {
			System.out.println("Usage <covariance.ser> <testDirectory>");
			System.exit(-1);
		}

		final PatternImportCovariance pic = (PatternImportCovariance) Serializer
				.getSerializer().deserializeFrom(args[0]);

		final SuggestIdiomsGivenImports sigi = new SuggestIdiomsGivenImports(
				pic);
		final File testDirectory = new File(args[1]);
		sigi.evaluateOnTest(testDirectory);
		sigi.printResults();
	}

	private final RecallStats stats = new RecallStats();

	private final PatternImportCovariance importCovariance;

	public SuggestIdiomsGivenImports(final PatternImportCovariance pic) {
		importCovariance = pic;
	}

	private void evaluateFile(final File f) throws IOException {
		final JavaASTExtractor ex = new JavaASTExtractor(false);
		final CompilationUnit ast = ex.getAST(f);

		// Get patterns in f
		final TreeNode<Integer> fileAst = importCovariance.getFormat().getTree(
				ast);
		final Set<Integer> patternsInFile = importCovariance
				.patternInFileId(fileAst);

		// Get imports in f
		final PackageInfoExtractor pie = new PackageInfoExtractor(ast);
		final Set<String> importedPackages = PatternImportCovariance
				.parseImports(pie.getImports());
		if (importedPackages.isEmpty()) {
			return;
		}

		// Get all patterns given the imports
		final Map<Integer, Double> patternScores = Maps.newTreeMap();
		for (final String packageName : importedPackages) {
			if (!importCovariance.getElementCooccurence().getRowValues()
					.contains(packageName)) {
				continue;
			}
			final List<ElementMutualInformation<Integer>> patternScoresForPackage = importCovariance
					.getElementCooccurence().getColumnMutualInformationFor(
							packageName);
			for (final ElementMutualInformation<Integer> prb : patternScoresForPackage) {
				if (patternScores.containsKey(prb.element)) {
					final Double previous = patternScores.get(prb.element);
					checkArgument(!Double.isNaN(previous));
					patternScores.put(prb.element,
							Math.max(prb.logProb, previous));
				} else {
					patternScores.put(prb.element, prb.logProb);
				}
			}
		}

		// Normalize (i.e. pow(,1/N))
		final SortedSet<Suggestion> suggestions = Sets.newTreeSet();
		for (final java.util.Map.Entry<Integer, Double> entry : patternScores
				.entrySet()) {
			final double score = entry.getValue();// / importedPackages.size();
			checkArgument(!Double.isNaN(score),
					"Score is NaN with value %s at the power of 1/%s",
					entry.getValue(), importedPackages.size());
			suggestions.add(new Suggestion(entry.getKey(), score));
		}

		if (SHOW_SUGGESTIONS) {
			printSuggestion(f, suggestions);
		}

		// Push to evaluation object
		stats.pushResults(patternsInFile, suggestions);
	}

	private void evaluateOnTest(final File testDirectory) {
		final Collection<File> allFiles = FileUtils
				.listFiles(testDirectory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);
		final ParallelThreadPool ptp = new ParallelThreadPool();

		for (final File f : allFiles) {
			ptp.pushTask(new Runnable() {
				@Override
				public void run() {
					try {
						evaluateFile(f);
					} catch (final IOException e) {
						LOGGER.warning("Error in file " + f + " "
								+ ExceptionUtils.getFullStackTrace(e));
					}
				}
			});
		}
		ptp.waitForTermination();
	}

	private void printResults() {
		stats.printStats();
	}

	/**
	 * Print the suggestions for a specific file.
	 * 
	 * @param file
	 * @param suggestions
	 */
	private void printSuggestion(final File file,
			final SortedSet<Suggestion> suggestions) {
		System.out.println("Suggestions for " + file);
		int i = 0;
		for (final Suggestion suggestion : suggestions) {
			if (i > 10) {
				break;
			}
			i++;
			try {
				PrintPatternsFromTsg.printIntTree(
						importCovariance.getFormat(),
						importCovariance.getPatternDictionary().get(
								suggestion.id));
			} catch (final Throwable e) {
				System.out.println("Could not print pattern");
			}
			System.out.println("Score: "
					+ String.format("%.2E", suggestion.score));
			System.out.println("----------------------------------");
		}

	}

}

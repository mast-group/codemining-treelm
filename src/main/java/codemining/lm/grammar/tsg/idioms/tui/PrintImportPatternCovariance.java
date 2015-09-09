/**
 * 
 */
package codemining.lm.grammar.tsg.idioms.tui;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import codemining.lm.grammar.tsg.idioms.tui.ElementCooccurence.Lift;
import codemining.util.SettingsLoader;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PrintImportPatternCovariance {

	private static int nTopImports = (int) SettingsLoader.getNumericSetting(
			"nTopImports", 500);

	private static int nTopPatterns = (int) SettingsLoader.getNumericSetting(
			"nTopPatterns", 500);

	private static int nTopPatternsToAdd = (int) SettingsLoader
			.getNumericSetting("nTopPatterns", 100);

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 1) {
			System.out.println("Usage <covariance.ser>");
			System.exit(-1);
		}

		final PatternImportCovariance pic = (PatternImportCovariance) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final PrintImportPatternCovariance printer = new PrintImportPatternCovariance(
				pic);
		printer.printTargetVectors();
	}

	private final PatternImportCovariance covariance;

	PrintImportPatternCovariance(final PatternImportCovariance covariance) {
		this.covariance = covariance;
	}

	/**
	 * @param packages
	 * @return
	 */
	protected List<String> getTopImports(final Multiset<String> packages) {
		final List<String> topImports = Lists.newArrayList();
		int i = 0;
		for (final Entry<String> packageNameEntry : Multisets
				.copyHighestCountFirst(packages).entrySet()) {
			if (i > nTopImports || packageNameEntry.getCount() < 5) {
				break;
			}
			topImports.add(packageNameEntry.getElement());
			i++;
		}
		Collections.sort(topImports);
		return topImports;
	}

	/**
	 * Get the nTopPatterns - nTopPatternsToAdd top patterns for the given top
	 * imports.
	 * 
	 * @param topImports
	 * @return
	 */
	protected Set<Integer> getTopPatternsForImports(
			final List<String> topImports) {
		final SortedSet<Lift<String, Integer>> topPatternsByLift = Sets
				.newTreeSet();
		for (final String packageName : topImports) {
			topPatternsByLift.addAll(covariance.getElementCooccurence()
					.getCooccuringElementsForRow(packageName));
		}

		final Set<Integer> topPatterns = Sets.newHashSet();
		for (final Lift<String, Integer> lift : topPatternsByLift) {
			if (topPatterns.size() > nTopPatterns - nTopPatternsToAdd) {
				break;
			}
			topPatterns.add(lift.column);
		}
		return topPatterns;
	}

	/**
	 * Output to stdout the non-normalized vectors containing the
	 * cross-correlation between packages and patterns.
	 */
	public void printTargetVectors() {

		final Multiset<String> packages = covariance.getElementCooccurence()
				.getRowMultiset();

		final List<String> topImports = getTopImports(packages);
		printTopImports(packages, topImports);

		final Set<Integer> topPatterns = getTopPatternsForImports(topImports);

		// Just add top patterns because of their count. This should be general
		// patterns
		for (final int patternId : Multisets.copyHighestCountFirst(
				covariance.getElementCooccurence().getColumnMultiset())
				.elementSet()) {
			if (topPatterns.size() > nTopPatterns) {
				break;
			}
			topPatterns.add(patternId);
		}

		printTopPatterns(topPatterns);

		printTopVectors(topImports, topPatterns);
	}

	/**
	 * @param packages
	 * @param topImports
	 */
	protected void printTopImports(final Multiset<String> packages,
			final List<String> topImports) {
		System.out.println("Top imports");
		for (final String packageName : topImports) {
			System.out.println(packageName + ":" + packages.count(packageName));
		}
	}

	/**
	 * @param topPatterns
	 */
	protected void printTopPatterns(final Set<Integer> topPatterns) {
		System.out.println("Top patterns");
		for (final int patternId : topPatterns) {
			try {
				PrintPatterns.printIntTree(covariance.getFormat(),
						covariance.getPatternDictionary().get(patternId));
			} catch (final Throwable e) {
				System.out.println("Could not print pattern");
			}
			System.out.println("------------------------------------------");
		}
	}

	/**
	 * @param topImports
	 * @param topPatterns
	 */
	protected void printTopVectors(final List<String> topImports,
			final Set<Integer> topPatterns) {
		for (final int patternId : topPatterns) {
			final List<Double> values = Lists.newArrayList();
			for (final String packageId : topImports) {
				values.add(Math.exp(covariance.getElementCooccurence()
						.getElementLogLift(packageId, patternId)));
			}

			for (int j = 0; j < values.size(); j++) {
				System.out.print(String.format("%.5E", values.get(j)) + ",");
			}
			System.out.println();
		}
	}

}

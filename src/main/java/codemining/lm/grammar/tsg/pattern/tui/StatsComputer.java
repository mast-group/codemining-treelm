/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import java.io.File;

import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.pattern.PatternStatsCalculator;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * TUI for computing the coverage of some TSG patterns.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class StatsComputer {

	public static void main(final String[] args) throws SerializationException {
		if (args.length != 4) {
			System.err
					.println("Usage <tsg> <directoryToComputeCoverage> <minPatternCountList> <minPatternSizeList>");
			System.exit(-1);
		}

		final JavaFormattedTSGrammar grammar = (JavaFormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final int[] minPatternCount = parseInt(args[2].split(","));
		final int[] minPatternSize = parseInt(args[3].split(","));

		final File directory = new File(args[1]);

		final PatternStatsCalculator pcc = new PatternStatsCalculator(
				grammar.getJavaTreeExtractor(), grammar, directory);

		pcc.printStatisticsFor(minPatternSize, minPatternCount);

	}

	static int[] parseInt(final String[] strVals) {
		final int[] vals = new int[strVals.length];
		for (int i = 0; i < strVals.length; i++) {
			vals[i] = Integer.parseInt(strVals[i]);
		}
		return vals;
	}
}

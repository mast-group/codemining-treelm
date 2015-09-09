/**
 *
 */
package codemining.lm.grammar.tsg.idioms.tui;

import java.io.File;
import java.util.logging.Logger;

import codemining.lm.grammar.java.ast.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tsg.FormattedTSGrammar;
import codemining.lm.grammar.tsg.idioms.PatternStatsCalculator;
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

		final FormattedTSGrammar grammar = (FormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final int[] minPatternCount = parseIntList(args[2].split(","));
		final int[] minPatternSize = parseIntList(args[3].split(","));

		final File directory = new File(args[1]);

		LOGGER.info("Finished loading, creating core structures");
		final PatternStatsCalculator pcc = new PatternStatsCalculator(
				(AbstractJavaTreeExtractor) grammar.getTreeExtractor(),
				grammar, directory);

		LOGGER.info("Initiating stats computation...");
		pcc.printStatisticsFor(minPatternSize, minPatternCount);

	}

	static int[] parseIntList(final String[] strVals) {
		final int[] vals = new int[strVals.length];
		for (int i = 0; i < strVals.length; i++) {
			vals[i] = Integer.parseInt(strVals[i]);
		}
		return vals;
	}

	private static final Logger LOGGER = Logger.getLogger(StatsComputer.class
			.getName());
}

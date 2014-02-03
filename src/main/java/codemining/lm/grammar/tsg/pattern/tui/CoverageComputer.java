/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;

import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.pattern.PatternCoverageCalculator;
import codemining.lm.grammar.tsg.pattern.PatternExtractor;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * TUI for computing the coverage of some TSG patterns.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class CoverageComputer {

	public static void main(final String[] args) throws SerializationException {
		if (args.length != 4) {
			System.err
					.println("Usage <tsg> <directoryToComputeCoverage> <minPatternCount> <minPatternSize>");
			System.exit(-1);
		}

		final JavaFormattedTSGrammar grammar = (JavaFormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final int minPatternCount = Integer.parseInt(args[2]);
		final int minPatternSize = Integer.parseInt(args[3]);
		final Set<TreeNode<Integer>> patterns = PatternExtractor
				.getPatternsFrom(grammar, minPatternCount, minPatternSize);

		final Collection<File> allFiles = FileUtils.listFiles(
				new File(args[1]), new SuffixFileFilter(".java"),
				DirectoryFileFilter.DIRECTORY);

		final PatternCoverageCalculator pcc = new PatternCoverageCalculator(
				grammar.getJavaTreeExtractor(), patterns);

		System.out.println("Coverage: "
				+ String.format("%.4f", pcc.getCoverageForFiles(allFiles)));

	}
}

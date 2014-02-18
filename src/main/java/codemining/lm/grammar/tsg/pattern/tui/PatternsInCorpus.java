/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.java.codeutils.JavaTokenizer;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.pattern.PatternExtractor;
import codemining.lm.grammar.tsg.pattern.PatternStatsCalculator;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Sets;

/**
 * Track the patterns in a corpus.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PatternsInCorpus {

	private static final Logger LOGGER = Logger
			.getLogger(PatternsInCorpus.class.getName());

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 4) {
			System.err
					.println("Usage <tsg> <minPatternCount> <minPatternSize> <corpusDir>");
			System.exit(-1);
		}

		final JavaFormattedTSGrammar grammar = (JavaFormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final int minPatternCount = Integer.parseInt(args[1]);
		final int minPatternSize = Integer.parseInt(args[2]);
		final AbstractJavaTreeExtractor format = grammar.getJavaTreeExtractor();
		final Set<TreeNode<TSGNode>> tsgPatterns = PatternExtractor
				.getTSGPatternsFrom(grammar, minPatternCount, minPatternSize);
		final Set<TreeNode<Integer>> patterns = Sets.newHashSet();
		for (final TreeNode<TSGNode> tsgPattern : tsgPatterns) {
			patterns.add(TSGNode.tsgTreeToInt(tsgPattern));
		}

		final File directory = new File(args[3]);
		final Collection<File> allFiles = FileUtils
				.listFiles(directory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);
		for (final File f : allFiles) {
			try {
				final TreeNode<Integer> fileAst = format.getTree(f);
				final Multiset<TreeNode<Integer>> filePatterns = PatternStatsCalculator
						.getPatternsForTree(fileAst, patterns);
				if (!filePatterns.isEmpty()) {
					printFileMatches(f, filePatterns, format);
				}
			} catch (final Exception e) {
				LOGGER.warning("Error in file " + f + " "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}
	}

	private static void printFileMatches(final File file,
			final Multiset<TreeNode<Integer>> filePatterns,
			final AbstractJavaTreeExtractor format) {
		System.out.println("----------------------------------------------");
		System.out.println("File " + file + " matches: ");
		for (final Entry<TreeNode<Integer>> pattern : filePatterns.entrySet()) {
			PrintPatternsFromTsg.printIntTree(format, pattern.getElement());
			System.out.println(pattern.getCount() + " times");
			System.out.println("__________________________________________");
		}

	}
}

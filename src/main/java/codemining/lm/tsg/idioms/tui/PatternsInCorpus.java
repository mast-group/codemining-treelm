/**
 *
 */
package codemining.lm.tsg.idioms.tui;

import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.ast.TreeNode;
import codemining.ast.java.AbstractJavaTreeExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.lm.tsg.FormattedTSGrammar;
import codemining.lm.tsg.idioms.PatternCorpus;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * Track down the patterns in a corpus.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class PatternsInCorpus {

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

		FormattedTSGrammar grammar = (FormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final int minPatternCount = Integer.parseInt(args[1]);
		final int minPatternSize = Integer.parseInt(args[2]);
		final AbstractJavaTreeExtractor format = (AbstractJavaTreeExtractor) grammar
				.getTreeExtractor();
		final Set<TreeNode<Integer>> patterns = PatternCorpus.getPatternsFrom(
				grammar, minPatternCount, minPatternSize);

		grammar = null; // Tell the GC that we don't need the grammar anymore.

		final File directory = new File(args[3]);
		final Collection<File> allFiles = FileUtils
				.listFiles(directory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);
		final Multimap<File, TreeNode<Integer>> filePatterns = ArrayListMultimap
				.create();
		for (final File f : allFiles) {
			try {
				final TreeNode<Integer> fileAst = format.getTree(f);
				filePatterns.putAll(f,
						PatternCorpus.getPatternsForTree(fileAst, patterns));
			} catch (final Exception e) {
				LOGGER.warning("Error in file " + f + " "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}
		printFileMatches(filePatterns, format);
	}

	private static void printFileMatches(
			final Multimap<File, TreeNode<Integer>> filePatterns,
			final AbstractJavaTreeExtractor format) {
		// Build reverse
		final Multimap<TreeNode<Integer>, File> patternsPerFile = ArrayListMultimap
				.create();
		for (final java.util.Map.Entry<File, TreeNode<Integer>> entry : filePatterns
				.entries()) {
			patternsPerFile.put(entry.getValue(), entry.getKey());
		}

		// now print
		for (final TreeNode<Integer> pattern : patternsPerFile.keySet()) {
			System.out
					.println("----------------------------------------------");
			try {
				PrintPatterns.printIntTree(format, pattern);
			} catch (final Throwable e) {
				System.out.println("Could not print pattern.");
			}
			final Collection<File> files = patternsPerFile.get(pattern);
			System.out.println(files.size() + " times in:");

			for (final File f : files) {
				System.out.println(f);
			}
		}

	}

	private static final Logger LOGGER = Logger
			.getLogger(PatternsInCorpus.class.getName());
}

/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
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
	 * @param grammar
	 * @param minPatternCount
	 * @param minPatternSize
	 * @return
	 */
	protected static Set<TreeNode<Integer>> getPatterns(
			final JavaFormattedTSGrammar grammar, final int minPatternCount,
			final int minPatternSize) {
		final Set<TreeNode<TSGNode>> tsgPatterns = PatternExtractor
				.getTSGPatternsFrom(grammar, minPatternCount, minPatternSize);
		final Set<TreeNode<Integer>> patterns = Sets.newHashSet();
		for (final TreeNode<TSGNode> tsgPattern : tsgPatterns) {
			patterns.add(TSGNode.tsgTreeToInt(tsgPattern));
		}
		return patterns;
	}

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
				if (pattern.partialMatch(currentNode,
						PatternStatsCalculator.BASE_EQUALITY_COMPARATOR, false)) {
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
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 4) {
			System.err
					.println("Usage <tsg> <minPatternCount> <minPatternSize> <corpusDir>");
			System.exit(-1);
		}

		JavaFormattedTSGrammar grammar = (JavaFormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final int minPatternCount = Integer.parseInt(args[1]);
		final int minPatternSize = Integer.parseInt(args[2]);
		final AbstractJavaTreeExtractor format = grammar.getJavaTreeExtractor();
		final Set<TreeNode<Integer>> patterns = getPatterns(grammar,
				minPatternCount, minPatternSize);

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
				filePatterns.putAll(f, getPatternsForTree(fileAst, patterns));
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
				PrintPatternsFromTsg.printIntTree(format, pattern);
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
}

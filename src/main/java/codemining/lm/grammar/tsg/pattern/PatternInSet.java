/**
 * 
 */
package codemining.lm.grammar.tsg.pattern;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jdt.core.dom.ASTNode;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.codeutils.JavaTokenizer;
import codemining.lm.grammar.java.ast.BinaryEclipseASTTreeExtractor;
import codemining.lm.grammar.java.ast.VariableTypeJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Serialize an object containing only the patterns seen in a specific corpus
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PatternInSet {

	private static final Logger LOGGER = Logger.getLogger(PatternInSet.class
			.getName());

	/**
	 * Return the list of patterns a specific tree.
	 */
	public static void getPatternsForTree(final TreeNode<Integer> tree,
			final Set<TreeNode<Integer>> patterns,
			final Set<TreeNode<Integer>> patternSeen) {
		final ArrayDeque<TreeNode<Integer>> toLook = new ArrayDeque<TreeNode<Integer>>();
		toLook.push(tree);

		// Do a pre-order visit
		while (!toLook.isEmpty()) {
			final TreeNode<Integer> currentNode = toLook.pop();
			// at each node check if we have a partial match with any of the
			// patterns
			for (final TreeNode<Integer> pattern : patterns) {
				if (!patternSeen.contains(pattern)
						&& pattern
								.partialMatch(
										currentNode,
										PatternStatsCalculator.BASE_EQUALITY_COMPARATOR,
										false)) {
					patternSeen.add(pattern);
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
	}

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 3) {
			System.err.println("Usage <tsg> <filterCorpusDir> <snippetDir>");
			System.exit(-1);
		}

		final JavaFormattedTSGrammar grammar = (JavaFormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final BinaryEclipseASTTreeExtractor format = (BinaryEclipseASTTreeExtractor) grammar
				.getJavaTreeExtractor();
		final VariableTypeJavaTreeExtractor typeExtractor = (VariableTypeJavaTreeExtractor) format
				.getBaseExtractor();

		final Set<TreeNode<TSGNode>> tsgPatterns = PatternExtractor
				.getTSGPatternsFrom(grammar, 0, 0);
		final Set<TreeNode<Integer>> patterns = Sets.newHashSet();

		for (final TreeNode<TSGNode> tsgPattern : tsgPatterns) {
			patterns.add(TSGNode.tsgTreeToInt(tsgPattern));
		}

		// Find the patterns seen in the text corpus
		final File directory = new File(args[1]);
		final Collection<File> allFiles = FileUtils
				.listFiles(directory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);
		final Set<TreeNode<Integer>> patternSeenInCorpus = Sets
				.newIdentityHashSet();
		for (final File f : allFiles) {
			try {
				final TreeNode<Integer> fileAst = format.getTree(f);
				getPatternsForTree(fileAst, patterns, patternSeenInCorpus);
			} catch (final IOException e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}

		final Set<TreeNode<Integer>> convertedPatterns = Sets
				.newIdentityHashSet();
		for (final TreeNode<Integer> pattern : patternSeenInCorpus) {
			convertedPatterns.add(typeExtractor.detempletize(pattern));
		}

		final List<String> snippets = (List<String>) Serializer.getSerializer()
				.deserializeFrom(args[2]);
		final Set<TreeNode<Integer>> snippetPatterns = Sets
				.newIdentityHashSet();
		final JavaASTExtractor astExtractor = new JavaASTExtractor(false);

		for (final String snippet : snippets) {
			final ASTNode node = astExtractor.getAST(snippet);
			final TreeNode<Integer> snippetTree = format.getTree(node);
			final TreeNode<Integer> detempletizedTree = typeExtractor
					.detempletize(snippetTree);
			getPatternsForTree(detempletizedTree, convertedPatterns,
					snippetPatterns);
		}

		final SortedMap<Integer, Set<TreeNode<Integer>>> nodeSizes = Maps
				.newTreeMap();
		for (final TreeNode<Integer> pattern : convertedPatterns) {
			final int size = pattern.getTreeSize();

			final Set<TreeNode<Integer>> nodesForSize;
			if (nodeSizes.containsKey(size)) {
				nodesForSize = nodeSizes.get(size);
			} else {
				nodesForSize = Sets.newIdentityHashSet();
				nodeSizes.put(size, nodesForSize);
			}
			nodesForSize.add(pattern);
		}

		final int[] testSizes = { 2, 5, 8, 10, 12, 15, 20, 30 };
		for (final int size : testSizes) {
			final SortedMap<Integer, Set<TreeNode<Integer>>> nodesOfThisSize = nodeSizes
					.subMap(size, Integer.MAX_VALUE);

			final Set<TreeNode<Integer>> allPatterns = Sets
					.newIdentityHashSet();
			for (final Set<TreeNode<Integer>> patternsForSize : nodesOfThisSize
					.values()) {
				allPatterns.addAll(patternsForSize);
			}

			final double recallForSize = ((double) Sets.intersection(
					allPatterns, snippetPatterns).size())
					/ allPatterns.size();
			System.out.println(size + "," + recallForSize);
		}
	}
}

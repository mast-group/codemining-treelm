/**
 *
 */
package codemining.lm.grammar.tsg.idioms;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jdt.core.dom.ASTNode;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.lm.grammar.java.ast.BinaryJavaAstTreeExtractor;
import codemining.lm.grammar.java.ast.VariableTypeJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.FormattedTSGrammar;
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

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 4) {
			System.err
			.println("Usage <tsg> <filterCorpusDir> <snippetDir> <minPatternCount>");
			System.exit(-1);
		}

		final FormattedTSGrammar grammar = (FormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final BinaryJavaAstTreeExtractor format = (BinaryJavaAstTreeExtractor) grammar
				.getTreeExtractor();
		final VariableTypeJavaTreeExtractor typeExtractor = (VariableTypeJavaTreeExtractor) format
				.getBaseExtractor();
		final int minPatternCount = Integer.parseInt(args[3]);

		final Set<TreeNode<Integer>> patterns = PatternCorpus.getPatternsFrom(
				grammar, minPatternCount, 5);

		// Find the patterns seen in the text corpus
		final File directory = new File(args[1]);
		final Set<TreeNode<Integer>> patternSeenInCorpus = PatternCorpus
				.patternsSeenInCorpus(format, patterns, directory);

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

		int countSnippetsMatchedAtLeastOnce = 0;
		double sumAvgMatchesPerNode = 0;
		for (final String snippet : snippets) {
			try {
				final ASTNode node = astExtractor.getBestEffortAstNode(snippet);
				final TreeNode<Integer> snippetTree = format.getTree(node);
				final TreeNode<Integer> detempletizedTree = typeExtractor
						.detempletize(snippetTree);
				final double avgMatchesPerNode = PatternCorpus
						.getPatternsForTree(detempletizedTree,
								convertedPatterns, snippetPatterns);
				if (!Double.isNaN(avgMatchesPerNode)) {
					countSnippetsMatchedAtLeastOnce++;
					sumAvgMatchesPerNode += avgMatchesPerNode;
				}
			} catch (final Exception e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}
		System.out.println("Snippets matched at least one:"
				+ countSnippetsMatchedAtLeastOnce);
		System.out.println("Duplication Ratio:" + sumAvgMatchesPerNode
				/ countSnippetsMatchedAtLeastOnce);

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

		final int[] testSizes = { 5, 8, 10, 12, 15, 20, 30 };
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

	static final Logger LOGGER = Logger.getLogger(PatternInSet.class.getName());
}

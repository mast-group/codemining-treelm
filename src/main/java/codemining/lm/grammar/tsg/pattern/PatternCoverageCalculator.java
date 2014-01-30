package codemining.lm.grammar.tsg.pattern;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

import codemining.java.codeutils.JavaTokenizer;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tree.TreeNode.NodeDataPair;

import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;

/**
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 */
class PatternCoverageCalculator {

	/**
	 * The tree format used to extract trees.
	 */
	protected final AbstractJavaTreeExtractor treeFormat;

	/**
	 * The patterns to be sought for.
	 */
	final Collection<TreeNode<Integer>> patterns;

	/**
	 * A predicate for comparing integer tree nodes.
	 */
	public static final Predicate<NodeDataPair<Integer>> BASE_EQUALITY_COMPARATOR = new Predicate<NodeDataPair<Integer>>() {

		@Override
		public boolean apply(final NodeDataPair<Integer> nodePair) {
			return nodePair.fromNode.equals(nodePair.toNode);
		}

	};

	public PatternCoverageCalculator(
			final AbstractJavaTreeExtractor treeFormat,
			final Collection<TreeNode<Integer>> patterns) {
		this.treeFormat = treeFormat;
		this.patterns = patterns;
	}

	/**
	 * Return a multiset containing the count of each pattern.
	 * 
	 * @param patternsPerFile
	 * @return
	 */
	public Multiset<TreeNode<Integer>> getPatternCount(
			final Multimap<File, TreeNode<Integer>> patternsPerFile) {
		final Multiset<TreeNode<Integer>> patternCounts = HashMultiset.create();
		for (final Entry<File, TreeNode<Integer>> filePatterns : patternsPerFile
				.entries()) {
			patternCounts.add(filePatterns.getValue());
		}

		return patternCounts;
	}

	/**
	 * Get a list of patterns for a whole file.
	 */
	public List<TreeNode<Integer>> getPatternsForFile(final File f)
			throws IOException {
		final List<TreeNode<Integer>> patterns = Lists.newArrayList();
		final TreeNode<Integer> tree = treeFormat.getTree(f);

		final ArrayDeque<TreeNode<Integer>> toLook = new ArrayDeque<TreeNode<Integer>>();
		toLook.push(tree);

		// Do a pre-visit
		while (!toLook.isEmpty()) {
			final TreeNode<Integer> currentNode = toLook.pop();
			// at each node check if we have a partial match with any of the
			// patterns
			for (final TreeNode<Integer> pattern : patterns) {
				if (pattern.partialMatch(currentNode, BASE_EQUALITY_COMPARATOR,
						true)) {
					patterns.add(pattern);
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

		return patterns;
	}

	/**
	 * Return a map of all the patterns that can be found in a single file.
	 */
	public Multimap<File, TreeNode<Integer>> getPatternsForFiles(
			final File directory) throws IOException {
		final Collection<File> allFiles = FileUtils
				.listFiles(directory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);

		final Multimap<File, TreeNode<Integer>> filePatterns = ArrayListMultimap
				.create();

		for (final File f : allFiles) {
			filePatterns.putAll(f, getPatternsForFile(f));
		}

		return filePatterns;

	}

}
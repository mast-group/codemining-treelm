package codemining.lm.grammar.tsg.pattern;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

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
 * class that can calculate the coverage statistics for a corpus.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 */
public class PatternCoverageCalculator {

	private static final Logger LOGGER = Logger
			.getLogger(PatternCoverageCalculator.class.getName());

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

	public double getCoverageForFile(final File f) throws IOException {
		final TreeNode<Integer> tree = treeFormat.getTree(f);
		final double treeSize = tree.getTreeSize();

		long patternTotalSize = 0;
		for (final TreeNode<Integer> pattern : getPatternsForTree(tree)) {
			patternTotalSize += pattern.getTreeSize();
		}

		return ((double) patternTotalSize) / treeSize;
	}

	public double getCoverageForFiles(final Collection<File> files) {
		int count = 0;
		double totalCoverage = 0;
		for (final File f : files) {
			try {
				totalCoverage += getCoverageForFile(f);
				count++;
			} catch (final IOException e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}
		return totalCoverage / count;
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
		final TreeNode<Integer> tree = treeFormat.getTree(f);
		return getPatternsForTree(tree);
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

	/**
	 * @param tree
	 * @return
	 */
	protected List<TreeNode<Integer>> getPatternsForTree(
			final TreeNode<Integer> tree) {
		final List<TreeNode<Integer>> treePatterns = Lists.newArrayList();
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

}
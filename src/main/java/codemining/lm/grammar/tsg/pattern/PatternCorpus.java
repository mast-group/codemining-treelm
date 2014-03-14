/**
 * 
 */
package codemining.lm.grammar.tsg.pattern;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

import codemining.java.codeutils.JavaTokenizer;
import codemining.lm.grammar.java.ast.JavaASTTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.util.SettingsLoader;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Sets;

/**
 * A class that contains patterns.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PatternCorpus implements Serializable {

	private static final long serialVersionUID = 8309734116605145468L;

	/**
	 * The minimum size of a TSG rule to be considered as a pattern.
	 */
	public static final int MIN_PATTERN_SIZE = (int) SettingsLoader
			.getNumericSetting("minSizePattern", 7);

	/**
	 * The minimum count of a TSG rule to be considered a pattern.
	 */
	public static final int MIN_PATTERN_COUNT = (int) SettingsLoader
			.getNumericSetting("minPatternCount", 10);

	/**
	 * Return the list of patterns of a specific tree.
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
	 * Get a set of patterns given the default min count and min size.
	 * 
	 * @param grammar
	 * @return
	 */
	public static Set<TreeNode<Integer>> getPatternsFrom(
			final JavaFormattedTSGrammar grammar) {
		return getPatternsFrom(grammar, MIN_PATTERN_COUNT, MIN_PATTERN_SIZE);
	}

	/**
	 * Return a list of patterns in the given TSG grammar.
	 * 
	 * @param grammar
	 * @return
	 */
	public static Set<TreeNode<Integer>> getPatternsFrom(
			final JavaFormattedTSGrammar grammar, final int minPatternCount,
			final int minPatternSize) {
		final Set<TreeNode<Integer>> patterns = Sets.newHashSet();
		for (final Multiset<TreeNode<TSGNode>> rules : grammar
				.getInternalGrammar().values()) {
			for (final Entry<TreeNode<TSGNode>> ruleEntry : rules.entrySet()) {
				if (isPattern(ruleEntry, minPatternCount, minPatternSize)) {
					patterns.add(TSGNode.tsgTreeToInt(ruleEntry.getElement()));
				}
			}
		}

		return patterns;
	}

	/**
	 * @param ruleEntry
	 * @return
	 */
	protected static boolean isPattern(
			final Entry<TreeNode<TSGNode>> ruleEntry,
			final int minPatternCount, final int minPatternSize) {
		return ruleEntry.getCount() >= minPatternCount
				&& ruleEntry.getElement().getTreeSize() >= minPatternSize;
	}

	/**
	 * The list of patterns.
	 */
	private final Set<TreeNode<Integer>> patterns = Sets.newHashSet();

	private final JavaASTTreeExtractor format;

	public PatternCorpus(final JavaASTTreeExtractor format) {
		this.format = format;
	}

	/**
	 * Extract all rules from the grammar as patterns.
	 * 
	 * @param grammar
	 */
	public void addFromGrammar(final JavaFormattedTSGrammar grammar,
			final int minPatternCount, final int minPatternSize) {
		patterns.addAll(getPatternsFrom(grammar, minPatternCount,
				minPatternSize));
	}

	/**
	 * Filter all patterns so that they are contained in at least one of the
	 * files.
	 * 
	 * @param directory
	 * @param grammar
	 */
	public void filterFromFiles(final File directory) {
		final Collection<File> allFiles = FileUtils
				.listFiles(directory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);

		// TODO

	}

}

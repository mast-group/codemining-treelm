/**
 * 
 */
package codemining.lm.grammar.tsg.pattern;

import java.util.Set;

import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.util.SettingsLoader;

import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Sets;

/**
 * Extract patterns from
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PatternExtractor {

	/**
	 * The minimum size of a TSG rule to be considered as a pattern.
	 */
	public static final int MIN_PATTERN_SIZE = (int) SettingsLoader
			.getNumericSetting("minSizePattern", 15);

	/**
	 * The minimum count of a TSG rule to be considered a pattern.
	 */
	public static final int MIN_PATTERN_COUNT = (int) SettingsLoader
			.getNumericSetting("minPatternCount", 15);

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

	public static Set<TreeNode<TSGNode>> getTSGPatternsFrom(
			final JavaFormattedTSGrammar grammar) {
		return getTSGPatternsFrom(grammar, MIN_PATTERN_COUNT, MIN_PATTERN_SIZE);
	}

	/**
	 * Get the patterns from the given grammar in TreeNode<TSGNode>
	 * 
	 * @param grammar
	 * @return
	 */
	public static Set<TreeNode<TSGNode>> getTSGPatternsFrom(
			final JavaFormattedTSGrammar grammar, final int minPatternCount,
			final int minPatternSize) {
		final Set<TreeNode<TSGNode>> patterns = Sets.newHashSet();
		for (final Multiset<TreeNode<TSGNode>> rules : grammar
				.getInternalGrammar().values()) {
			for (final Entry<TreeNode<TSGNode>> ruleEntry : rules.entrySet()) {
				if (isPattern(ruleEntry, minPatternCount, minPatternSize)) {
					patterns.add(ruleEntry.getElement());
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
				&& TreeNode.getTreeSize(ruleEntry.getElement()) >= minPatternSize;
	}

	private PatternExtractor() {
		//
	}

}

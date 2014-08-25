/**
 *
 */
package codemining.lm.grammar.tsg.pattern;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.lm.grammar.java.ast.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.util.CollectionUtil;
import codemining.util.SettingsLoader;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Sets;

/**
 * A class that contains patterns.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
@DefaultSerializer(JavaSerializer.class)
public class PatternCorpus implements Serializable {

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
	 * Return the list of patterns a specific tree.
	 */
	public static double getPatternsForTree(final TreeNode<Integer> tree,
			final Set<TreeNode<Integer>> patterns,
			final Set<TreeNode<Integer>> patternSeen) {
		final ArrayDeque<TreeNode<Integer>> toLook = new ArrayDeque<TreeNode<Integer>>();
		toLook.push(tree);
		final Map<TreeNode<Integer>, Long> matches = Maps.newIdentityHashMap();

		// Do a pre-order visit
		while (!toLook.isEmpty()) {
			final TreeNode<Integer> currentNode = toLook.pop();
			// at each node check if we have a partial match with any of the
			// patterns
			for (final TreeNode<Integer> pattern : patterns) {
				if (pattern.partialMatch(currentNode,
						PatternStatsCalculator.BASE_EQUALITY_COMPARATOR, false)) {
					patternSeen.add(pattern);
					for (final TreeNode<Integer> node : currentNode
							.getOverlappingNodesWith(pattern)) {
						if (matches.containsKey(node)) {
							matches.put(node, matches.get(node) + 1L);
						} else {
							matches.put(node, 1L);
						}
					}
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

		long sumOfMatches = 0;
		for (final long count : matches.values()) {
			sumOfMatches += count;
		}

		return ((double) sumOfMatches) / matches.size();
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

	public static void main(final String[] args) throws SerializationException {
		if (args.length < 3) {
			System.err
					.println("Usage <tsg.ser> <minPatternCount> <minPatternSize> [<minTimesInFilterDir> <filterDir>...]");
			System.exit(-1);
		}

		final JavaFormattedTSGrammar grammar = (JavaFormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);

		final int minCount = Integer.parseInt(args[1]);
		final int minSize = Integer.parseInt(args[2]);

		final PatternCorpus corpus = new PatternCorpus(
				grammar.getTreeExtractor());
		corpus.addFromGrammar(grammar, minCount, minSize);

		if (args.length >= 5) {
			final int nTimesSeen = Integer.parseInt(args[3]);
			final List<File> directories = Lists.newArrayList();
			for (int i = 4; i < args.length; i++) {
				directories.add(new File(args[i]));
			}
			corpus.filterFromFiles(directories, nTimesSeen);
		}

		Serializer.getSerializer().serialize(corpus, "patterns.ser");
	}

	/**
	 * @param format
	 * @param patterns
	 * @param directory
	 * @return
	 */
	public static Set<TreeNode<Integer>> patternsSeenInCorpus(
			final AbstractJavaTreeExtractor format,
			final Set<TreeNode<Integer>> patterns, final File directory) {
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
				PatternInSet.LOGGER
						.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}
		return patternSeenInCorpus;
	}

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

	static final Logger LOGGER = Logger
			.getLogger(PatternCorpus.class.getName());

	/**
	 * The list of patterns.
	 */
	private final Set<TreeNode<Integer>> patterns = Sets.newHashSet();

	private final AbstractJavaTreeExtractor format;

	public PatternCorpus(final AbstractJavaTreeExtractor format) {
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

	public void addPattern(final TreeNode<Integer> tree) {
		patterns.add(tree);
	}

	/**
	 * Filter all patterns so that they are contained in at least one of the
	 * files.
	 *
	 * @param directory
	 * @param nSeenInFiles
	 *            number of times seen in the files.
	 */
	public void filterFromFiles(final Collection<File> directories,
			final int nSeenInFiles) {
		final Multiset<TreeNode<Integer>> patternsSeen = HashMultiset.create();
		for (final File directory : directories) {
			final Collection<File> directoryFiles = FileUtils.listFiles(
					directory, JavaTokenizer.javaCodeFileFilter,
					DirectoryFileFilter.DIRECTORY);
			for (final File f : directoryFiles) {
				try {
					// We add the patterns once per file.
					patternsSeen.addAll(getPatternsFromTree(format.getTree(f))
							.elementSet());
				} catch (final IOException e) {
					LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
				}
			}

		}
		// patternsSeen now contains the number of files that each pattern has
		// been seen.

		final Set<TreeNode<Integer>> toKeep = CollectionUtil
				.getElementsUpToCount(nSeenInFiles, patternsSeen);
		patterns.retainAll(toKeep);
	}

	public AbstractJavaTreeExtractor getFormat() {
		return format;
	}

	public Set<TreeNode<Integer>> getNodesCovered(final File f)
			throws IOException {
		return getNodesCovered(format.getTree(f));
	}

	public Set<TreeNode<Integer>> getNodesCovered(final String snippet)
			throws Exception {
		final JavaASTExtractor ex = new JavaASTExtractor(false);
		return getNodesCovered(format.getTree(ex.getBestEffortAstNode(snippet)));
	}

	/**
	 * Return the set of covered nodes
	 *
	 * @param tree
	 * @return
	 */
	public Set<TreeNode<Integer>> getNodesCovered(final TreeNode<Integer> tree) {
		final Set<TreeNode<Integer>> overlappingNodes = Sets
				.newIdentityHashSet();

		final ArrayDeque<TreeNode<Integer>> toLook = new ArrayDeque<TreeNode<Integer>>();
		toLook.push(tree);

		// Do a pre-order visit
		while (!toLook.isEmpty()) {
			final TreeNode<Integer> currentNode = toLook.pop();
			// at each node check if we have a partial match with the
			// current patterns
			for (final TreeNode<Integer> pattern : patterns) {
				if (pattern.partialMatch(currentNode,
						PatternStatsCalculator.BASE_EQUALITY_COMPARATOR, false)) {
					overlappingNodes.addAll(currentNode
							.getOverlappingNodesWith(pattern));

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

		return overlappingNodes;
	}

	public Set<TreeNode<Integer>> getPatterns() {
		return patterns;
	}

	public Multiset<TreeNode<Integer>> getPatternsFrom(final File f)
			throws IOException {
		return getPatternsFromTree(format.getTree(f));
	}

	public Multiset<TreeNode<Integer>> getPatternsFrom(final String snippet)
			throws Exception {
		final JavaASTExtractor ex = new JavaASTExtractor(false);
		return getPatternsFromTree(format.getTree(ex
				.getBestEffortAstNode(snippet)));
	}

	/**
	 * Return the set of patterns for this tree.
	 */
	public Multiset<TreeNode<Integer>> getPatternsFromTree(
			final TreeNode<Integer> tree) {
		return getPatternsForTree(tree, patterns);
	}

}

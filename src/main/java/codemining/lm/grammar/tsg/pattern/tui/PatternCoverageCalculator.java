/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.lm.grammar.java.ast.BinaryEclipseASTTreeExtractor;
import codemining.lm.grammar.java.ast.VariableTypeJavaTreeExtractor;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.pattern.PatternCorpus;
import codemining.util.parallel.ParallelThreadPool;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;

/**
 * Return the number of idioms matched per code token.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PatternCoverageCalculator {

	public static class Results {
		long nNodesMatched = 0;
		long nNodes = 0;

		Set<TreeNode<Integer>> patternsMatched = Sets.newHashSet();

		public synchronized final void addStat(final long nNodesMatched,
				final long nNodes, final Set<TreeNode<Integer>> patternsMatched) {
			this.nNodesMatched += nNodesMatched;
			this.nNodes += nNodes;
			this.patternsMatched.addAll(patternsMatched);
		}

		@Override
		public String toString() {
			return "Coverage: "
					+ String.format("%.2E", (((double) nNodesMatched) / nNodes))
					+ " nTokens:" + nNodes + " nIdioms:" + nNodesMatched;
		}
	}

	private static final Logger LOGGER = Logger
			.getLogger(PatternCoverageCalculator.class.getName());

	private static void computeCoverage(final File f, final Results res,
			final PatternCorpus patterns) {
		try {
			final TreeNode<Integer> tree = patterns.getFormat().getTree(f);
			final TreeNode<Integer> debinTree = detempletizeTree(tree, patterns);
			final Set<TreeNode<Integer>> matchedNodes = patterns
					.getNodesCovered(debinTree);
			final Multiset<TreeNode<Integer>> patternsMatched = patterns
					.getPatternsFromTree(debinTree);
			res.addStat(matchedNodes.size(), debinTree.getTreeSize(),
					patternsMatched.elementSet());
		} catch (final IOException e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		}
	}

	private static void computeCoverage(final String snippet,
			final Results res, final PatternCorpus patterns) throws Exception {
		final JavaASTExtractor ex = new JavaASTExtractor(false);
		final TreeNode<Integer> tree = patterns.getFormat().getTree(
				ex.getBestEffortAstNode(snippet));
		final TreeNode<Integer> debinTree = detempletizeTree(tree, patterns);
		final Set<TreeNode<Integer>> matchedNodes = patterns
				.getNodesCovered(debinTree);
		final Multiset<TreeNode<Integer>> patternsMatched = patterns
				.getPatternsFromTree(debinTree);
		final int nTokens = getNumTokens(snippet);
		if (nTokens > 20) {
			res.addStat(matchedNodes.size(), debinTree.getTreeSize(),
					patternsMatched.elementSet());
		}
	}

	/**
	 * Detempletize the whole pattern corpus.
	 * 
	 * @param patterns
	 * @return
	 */
	private static PatternCorpus detempletizeCorpus(final PatternCorpus patterns) {
		checkArgument(patterns.getFormat().getClass()
				.equals(BinaryEclipseASTTreeExtractor.class));
		final BinaryEclipseASTTreeExtractor binaryFormat = (BinaryEclipseASTTreeExtractor) patterns
				.getFormat();
		final AbstractJavaTreeExtractor baseExtractor = binaryFormat
				.getBaseExtractor();

		checkArgument(baseExtractor.getClass().equals(
				VariableTypeJavaTreeExtractor.class));
		final VariableTypeJavaTreeExtractor varExtractor = (VariableTypeJavaTreeExtractor) baseExtractor;

		final PatternCorpus newPatterns = new PatternCorpus(binaryFormat);
		for (final TreeNode<Integer> pattern : patterns.getPatterns()) {
			newPatterns.addPattern(varExtractor.detempletize(pattern));
		}

		return newPatterns;
	}

	/**
	 * Detempletize the whole pattern corpus.
	 * 
	 * @param patterns
	 * @return
	 */
	private static TreeNode<Integer> detempletizeTree(
			final TreeNode<Integer> pattern, final PatternCorpus patterns) {
		checkArgument(patterns.getFormat().getClass()
				.equals(BinaryEclipseASTTreeExtractor.class));
		final BinaryEclipseASTTreeExtractor binaryFormat = (BinaryEclipseASTTreeExtractor) patterns
				.getFormat();
		final AbstractJavaTreeExtractor baseExtractor = binaryFormat
				.getBaseExtractor();

		checkArgument(baseExtractor.getClass().equals(
				VariableTypeJavaTreeExtractor.class));
		final VariableTypeJavaTreeExtractor varExtractor = (VariableTypeJavaTreeExtractor) baseExtractor;

		return varExtractor.detempletize(pattern);
	}

	private static int getNumTokens(final String snippet) {
		final JavaTokenizer tokenizer = new JavaTokenizer();
		return tokenizer.tokenListFromCode(snippet.toCharArray()).size() - 2;
	}

	public static void main(final String[] args) throws SerializationException {
		if (args.length != 3) {
			System.err
					.println("Usage <PatternCorpus.ser> directory|file <path>");
			System.exit(-1);
		}

		final PatternCorpus originalPatterns = (PatternCorpus) Serializer
				.getSerializer().deserializeFrom(args[0]);

		// detempletize
		final PatternCorpus patterns = detempletizeCorpus(originalPatterns);

		final Results res = new Results();
		final ParallelThreadPool ptp = new ParallelThreadPool();

		if (args[1].equals("directory")) {
			final Collection<File> allFiles = FileUtils.listFiles(new File(
					args[2]), JavaTokenizer.javaCodeFileFilter,
					DirectoryFileFilter.DIRECTORY);
			for (final File f : allFiles) {
				ptp.pushTask(new Runnable() {
					@Override
					public void run() {
						computeCoverage(f, res, patterns);
					}
				});
			}

		} else if (args[1].equals("file")) {
			final List<String> snippets = (List<String>) Serializer
					.getSerializer().deserializeFrom(args[2]);
			for (final String snippet : snippets) {
				ptp.pushTask(new Runnable() {
					@Override
					public void run() {
						try {
							computeCoverage(snippet, res, patterns);
						} catch (final Exception e) {
							LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
						}
					}
				});
			}
		} else {
			System.err
					.println("Unrecognizable type. Should be either directory or file");
		}

		ptp.waitForTermination();

		System.out.println(res);
		System.out.println("Precison: " + ((double) res.patternsMatched.size())
				/ patterns.getPatterns().size());
	}
}

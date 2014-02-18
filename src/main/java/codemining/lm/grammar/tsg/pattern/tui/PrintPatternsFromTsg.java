/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import java.util.Set;

import codemining.lm.grammar.tree.ASTNodeSymbol;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.pattern.PatternExtractor;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * Print TSG patterns given a TSG.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PrintPatternsFromTsg {

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 3) {
			System.err
					.println("Usage <tsg> <minPatternCount> <minPatternSize>");
			System.exit(-1);
		}

		final JavaFormattedTSGrammar grammar = (JavaFormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final int minPatternCount = Integer.parseInt(args[1]);
		final int minPatternSize = Integer.parseInt(args[2]);
		final Set<TreeNode<TSGNode>> patterns = PatternExtractor
				.getTSGPatternsFrom(grammar, minPatternCount, minPatternSize);

		for (final TreeNode<TSGNode> pattern : patterns) {
			try {
				System.out
						.println("------------------------------------------------------");
				printPattern(grammar, pattern);
			} catch (final Throwable e) {
				System.out.println("Error printing.");
			}

		}

	}

	/**
	 * @param grammar
	 * @param format
	 * @param intTree
	 */
	public static void printIntTree(final AbstractJavaTreeExtractor format,
			final TreeNode<Integer> intTree) {
		System.out.println(intTree.toString(format.getTreePrinter()));
		System.out
				.println("______________________________________________________");
		if (format.getSymbol(intTree.getData()).nodeType == ASTNodeSymbol.MULTI_NODE) {
			final StringBuffer sb = new StringBuffer();
			format.printMultinode(sb, intTree);
			System.out.println(sb.toString());
		} else {
			System.out.println(format.getASTFromTree(intTree));
		}
	}

	/**
	 * @param grammar
	 * @param format
	 * @param pattern
	 */
	public static void printPattern(final JavaFormattedTSGrammar grammar,
			final TreeNode<TSGNode> pattern) {
		final AbstractJavaTreeExtractor format = grammar.getJavaTreeExtractor();

		final TreeNode<Integer> intTree = TSGNode.tsgTreeToInt(pattern);
		printIntTree(format, intTree);

		final TreeNode<TSGNode> tsgTree = pattern;
		final int count = grammar.countTreeOccurences(tsgTree);
		final int totalProductions = grammar.countTreesWithRoot(tsgTree
				.getData());
		final double probability = ((double) count) / totalProductions;
		final int size = pattern.getTreeSize();
		System.out.println("Count: " + count + " Size:" + size + " Prob:"
				+ String.format("%.4f", probability));
		System.out
				.println("______________________________________________________");
	}

}

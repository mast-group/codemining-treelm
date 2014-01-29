/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import java.util.Set;

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
		final AbstractJavaTreeExtractor format = grammar.getJavaTreeExtractor();
		final Set<TreeNode<TSGNode>> patterns = PatternExtractor
				.getTSGPatternsFrom(grammar, minPatternCount, minPatternSize);

		for (final TreeNode<TSGNode> pattern : patterns) {
			System.out
					.println("------------------------------------------------------");
			final TreeNode<Integer> intTree = TSGNode.tsgTreeToInt(pattern);
			System.out.println(intTree.toString(format.getTreePrinter()));
			System.out
					.println("______________________________________________________");
			System.out.println(format.getASTFromTree(intTree));
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

}

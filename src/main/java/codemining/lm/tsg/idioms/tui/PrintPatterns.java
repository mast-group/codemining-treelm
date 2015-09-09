/**
 *
 */
package codemining.lm.tsg.idioms.tui;

import codemining.ast.AbstractTreeExtractor;
import codemining.ast.AstNodeSymbol;
import codemining.ast.TreeNode;
import codemining.lm.tsg.idioms.PatternCorpus;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * Print TSG patterns given a TSG.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class PrintPatterns {

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 1) {
			System.err.println("Usage <patternCorpus.ser>");
			System.exit(-1);
		}

		final PatternCorpus patterns = (PatternCorpus) Serializer
				.getSerializer().deserializeFrom(args[0]);

		for (final TreeNode<Integer> pattern : patterns.getPatterns()) {
			try {
				System.out
						.println("------------------------------------------------------");
				printPattern(patterns.getFormat(), pattern);
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
	public static void printIntTree(final AbstractTreeExtractor format,
			final TreeNode<Integer> intTree) {
		System.out.println(intTree.toString(t -> format.getTreePrinter()
				.convertTreeToString(t)));
		System.out
				.println("______________________________________________________");
		if (format.getSymbol(intTree.getData()).nodeType == AstNodeSymbol.MULTI_NODE) {
			final StringBuffer sb = new StringBuffer();
			format.printMultinode(sb, intTree);
			System.out.println(sb.toString());
		} else {
			System.out.println(format.getCodeFromTree(intTree));
		}
	}

	/**
	 * @param grammar
	 * @param format
	 * @param pattern
	 */
	public static void printPattern(final AbstractTreeExtractor format,
			final TreeNode<Integer> intTree) {
		printIntTree(format, intTree);
		System.out
				.println("______________________________________________________");
	}

}

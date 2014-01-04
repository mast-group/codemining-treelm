/**
 * 
 */
package codemining.lm.grammar.tui;

import java.io.File;
import java.io.IOException;
import java.util.List;

import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;

/**
 * A compact AST tree printer.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class TreePrinterUtility {

	/**
	 * @param args
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws IOException
	 */
	public static void main(final String[] args) throws InstantiationException,
			IllegalAccessException, ClassNotFoundException, IOException {
		if (args.length != 2) {
			System.err.println("Usage <file> <treeFormat>");
			return;
		}

		final AbstractJavaTreeExtractor format = (AbstractJavaTreeExtractor) (Class
				.forName(args[1]).newInstance());
		final TreeNode<Integer> node = format.getTree(new File(args[0]));
		final StringBuffer buf = new StringBuffer();
		treePrinterHelper(buf, node, "", format);
		System.out.println(buf.toString());

	}

	public static void treePrinterHelper(final StringBuffer buffer,
			final TreeNode<Integer> currentNode, final String prefix,
			final AbstractJavaTreeExtractor format) {
		buffer.append(prefix);
		buffer.append(currentNode.getData() + ":");
		buffer.append(format.getSymbol(currentNode.getData()));
		buffer.append('\n');
		for (final List<TreeNode<Integer>> childProperties : currentNode
				.getChildrenByProperty()) {
			for (final TreeNode<Integer> child : childProperties) {
				treePrinterHelper(buffer, child, prefix + "-", format);
			}
		}

	}
}

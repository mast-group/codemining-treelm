/**
 *
 */
package codemining.ast.tui;

import java.io.File;
import java.io.IOException;
import java.util.List;

import codemining.ast.AbstractTreeExtractor;
import codemining.ast.TreeNode;
import codemining.ast.java.AbstractJavaTreeExtractor;

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

		final AbstractTreeExtractor format = (AbstractTreeExtractor) (Class
				.forName(args[1]).newInstance());
		final TreeNode<Integer> node = format.getTree(new File(args[0]));
		final StringBuffer buf = new StringBuffer();
		treePrinterHelper(buf, node, "", format);
		System.out.println(buf.toString());

	}

	public static void treePrinterHelper(final StringBuffer sb,
			final TreeNode<Integer> currentNode, final String prefix,
			final AbstractTreeExtractor format) {
		sb.append(prefix);
		sb.append(currentNode.getData() + ":");
		sb.append(format.getSymbol(currentNode.getData()).toString(
				AbstractJavaTreeExtractor.JAVA_NODETYPE_CONVERTER));
		sb.append('\n');
		for (final List<TreeNode<Integer>> childProperties : currentNode
				.getChildrenByProperty()) {
			for (final TreeNode<Integer> child : childProperties) {
				treePrinterHelper(sb, child, prefix + "-", format);
			}
		}

	}
}

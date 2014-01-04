/**
 * 
 */
package codemining.lm.grammar.cfg.tui;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.eclipse.jdt.core.dom.ASTNode;

import codemining.lm.grammar.cfg.ContextFreeGrammar;
import codemining.lm.grammar.java.ast.BinaryEclipseASTTreeExtractor;
import codemining.lm.grammar.java.ast.ParentTypeAnnotatedEclipseASTExtractor;
import codemining.lm.grammar.tree.TreeNode;

/**
 * Generate random code for a PCFG
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class GenerateRandomCode {

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(final String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("Usage: <trainDirectory> <N>");
			return;
		}

		final BinaryEclipseASTTreeExtractor treeExtractor = new BinaryEclipseASTTreeExtractor(
				new ParentTypeAnnotatedEclipseASTExtractor());
		final ContextFreeGrammar cfg = new ContextFreeGrammar(treeExtractor);

		final Collection<File> files = FileUtils.listFiles(new File(args[0]),
				cfg.modelledFilesFilter(), DirectoryFileFilter.DIRECTORY);

		cfg.trainModel(files);

		for (int i = 0; i < Integer.parseInt(args[1]); i++) {
			final TreeNode<Integer> randomTree = cfg.generateRandom();
			final ASTNode ast = treeExtractor.getASTFromTree(randomTree);

			System.out.println(ast.toString());
			System.out.println("-----------------------------");
		}

	}

	private GenerateRandomCode() {
	}
}

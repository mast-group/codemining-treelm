/**
 *
 */
package codemining.lm.cfg.tui;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

import codemining.ast.TreeNode;
import codemining.ast.java.BinaryJavaAstTreeExtractor;
import codemining.ast.java.ParentTypeAnnotatedJavaAstExtractor;
import codemining.lm.cfg.ContextFreeGrammar;

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

		final BinaryJavaAstTreeExtractor treeExtractor = new BinaryJavaAstTreeExtractor(
				new ParentTypeAnnotatedJavaAstExtractor());
		final ContextFreeGrammar cfg = new ContextFreeGrammar(treeExtractor);

		final Collection<File> files = FileUtils.listFiles(new File(args[0]),
				cfg.modelledFilesFilter(), DirectoryFileFilter.DIRECTORY);

		cfg.trainModel(files);

		for (int i = 0; i < Integer.parseInt(args[1]); i++) {
			final TreeNode<Integer> randomTree = cfg.generateRandom();
			final String code = treeExtractor.getCodeFromTree(randomTree);

			System.out.println(code);
			System.out.println("-----------------------------");
		}

	}

	private GenerateRandomCode() {
	}
}

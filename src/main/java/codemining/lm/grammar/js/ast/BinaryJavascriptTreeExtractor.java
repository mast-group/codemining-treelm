/**
 *
 */
package codemining.lm.grammar.js.ast;

import org.eclipse.wst.jsdt.core.dom.ASTNode;

import codemining.lm.grammar.tree.AstNodeSymbol;
import codemining.lm.grammar.tree.TreeBinarizer;
import codemining.lm.grammar.tree.TreeNode;

/**
 * A binary javascript tree extractor.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class BinaryJavascriptTreeExtractor extends JavascriptTreeExtractor {

	private final JavascriptTreeExtractor base;

	private final TreeBinarizer binarizer;

	public BinaryJavascriptTreeExtractor(
			final JavascriptTreeExtractor baseExtractor) {
		super(null);
		base = baseExtractor;
		binarizer = new TreeBinarizer(baseExtractor);
	}

	public BinaryJavascriptTreeExtractor(
			final JavascriptTreeExtractor baseExtractor,
			final boolean annotateMultinodes) {
		super(null);
		base = baseExtractor;
		binarizer = new TreeBinarizer(baseExtractor, annotateMultinodes);
	}

	@Override
	public ASTNode getASTFromTree(final TreeNode<Integer> tree) {
		final TreeNode<Integer> debinarized = binarizer.debinarize(tree);

		return base.getASTFromTree(debinarized);
	}

	public JavascriptTreeExtractor getBaseExtractor() {
		return base;
	}

	public TreeBinarizer getBinarizer() {
		return binarizer;
	}

	@Override
	public TreeNode<Integer> getKeyForCompilationUnit() {
		return base.getKeyForCompilationUnit();
	}

	@Override
	public synchronized int getOrAddSymbolId(final AstNodeSymbol symbol) {
		return base.getOrAddSymbolId(symbol);
	}

	@Override
	public final AstNodeSymbol getSymbol(final int key) {
		return base.getSymbol(key);
	}

	@Override
	public TreeNode<Integer> getTree(final ASTNode node) {
		final TreeNode<Integer> tree = base.getTree(node);
		return binarizer.binarizeTree(tree);
	}

}

/**
 *
 */
package codemining.lm.grammar.java.ast;

import org.eclipse.jdt.core.dom.ASTNode;

import codemining.lm.grammar.tree.AstNodeSymbol;
import codemining.lm.grammar.tree.TreeBinarizer;
import codemining.lm.grammar.tree.TreeNode;

/**
 * A binary tree extractor.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class BinaryJavaAstTreeExtractor extends AbstractJavaTreeExtractor {

	private static final long serialVersionUID = -2977736516409659157L;

	private final AbstractJavaTreeExtractor base;

	private final TreeBinarizer binarizer;

	public BinaryJavaAstTreeExtractor(
			final AbstractJavaTreeExtractor baseExtractor) {
		super(null);
		base = baseExtractor;
		binarizer = new TreeBinarizer(baseExtractor);
	}

	public BinaryJavaAstTreeExtractor(
			final AbstractJavaTreeExtractor baseExtractor,
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

	public AbstractJavaTreeExtractor getBaseExtractor() {
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

/**
 * 
 */
package codemining.lm.grammar.java.ast;

import org.eclipse.jdt.core.dom.ASTNode;

import codemining.lm.grammar.tree.ASTNodeSymbol;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;

/**
 * A binary tree extractor.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class BinaryEclipseASTTreeExtractor extends AbstractJavaTreeExtractor {

	private static final long serialVersionUID = -2977736516409659157L;

	private final AbstractJavaTreeExtractor base;

	private final TreeBinarizer binarizer;

	public BinaryEclipseASTTreeExtractor(
			final AbstractJavaTreeExtractor baseExtractor) {
		super(null);
		base = baseExtractor;
		binarizer = new TreeBinarizer(baseExtractor);
	}

	public BinaryEclipseASTTreeExtractor(
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
	public synchronized int getOrAddSymbolId(final ASTNodeSymbol symbol) {
		return base.getOrAddSymbolId(symbol);
	}

	@Override
	public final ASTNodeSymbol getSymbol(final Integer key) {
		return base.getSymbol(key);
	}

	@Override
	public TreeNode<Integer> getTree(final ASTNode node) {
		final TreeNode<Integer> tree = base.getTree(node);
		return binarizer.binarizeTree(tree);
	}

}

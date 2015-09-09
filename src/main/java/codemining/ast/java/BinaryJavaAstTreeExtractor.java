/**
 *
 */
package codemining.ast.java;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;

import com.google.common.collect.BiMap;
import com.google.common.collect.Maps;

import codemining.ast.AstNodeSymbol;
import codemining.ast.TreeBinarizer;
import codemining.ast.TreeNode;

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

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.grammar.tree.AbstractTreeExtractor#getNodeAlphabet()
	 */
	@Override
	public BiMap<Integer, AstNodeSymbol> getNodeAlphabet() {
		return base.getNodeAlphabet();
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

	@Override
	public Map<ASTNode, TreeNode<Integer>> getTreeMap(final ASTNode node) {
		final Map<ASTNode, TreeNode<Integer>> baseTreeMap = base
				.getTreeMap(node);
		final Map<TreeNode<Integer>, TreeNode<Integer>> binarizationMappings = Maps
				.newIdentityHashMap();
		binarizer.binarizeTree(baseTreeMap.get(node), binarizationMappings);

		baseTreeMap.entrySet().forEach(
				entry -> entry.setValue(checkNotNull(binarizationMappings
						.get(entry.getValue()))));

		return baseTreeMap;
	}
}

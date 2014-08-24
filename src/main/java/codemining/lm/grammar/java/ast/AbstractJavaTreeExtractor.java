package codemining.lm.grammar.java.ast;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map.Entry;

import org.eclipse.jdt.core.dom.ASTNode;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.ParseType;
import codemining.lm.grammar.tree.AbstractTreeExtractor;
import codemining.lm.grammar.tree.AstNodeSymbol;
import codemining.lm.grammar.tree.TreeNode;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.base.Function;
import com.google.common.collect.BiMap;

/**
 * An abstract class representing the conversion of Eclipse's ASTNode to
 * ASTNodeSymbols and trees. It also includes the alphabet. Thread safe.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
@DefaultSerializer(JavaSerializer.class)
public abstract class AbstractJavaTreeExtractor extends AbstractTreeExtractor
implements Serializable {

	/**
	 * A node printer using the symbols.
	 */
	private final class NodePrinter implements
	Function<TreeNode<Integer>, String> {

		@Override
		public String apply(final TreeNode<Integer> node) {
			return getSymbol(node.getData()).toString(JAVA_NODETYPE_CONVERTER);
		}

	}

	private static final long serialVersionUID = -4515326266227881706L;

	public static final Function<Integer, String> JAVA_NODETYPE_CONVERTER = new Function<Integer, String>() {

		@Override
		public String apply(Integer nodeType) {
			return ASTNode.nodeClassForType(nodeType).getSimpleName();
		}
	};

	public AbstractJavaTreeExtractor() {
		super();
	}

	protected AbstractJavaTreeExtractor(
			final BiMap<Integer, AstNodeSymbol> alphabet) {
		super(alphabet);
	}

	/**
	 * Get the ASTNode given the tree.
	 *
	 * @param tree
	 * @return
	 */
	public abstract ASTNode getASTFromTree(final TreeNode<Integer> tree);

	@Override
	public TreeNode<Integer> getKeyForCompilationUnit() {
		for (final Entry<Integer, AstNodeSymbol> entry : nodeAlphabet
				.entrySet()) {
			if (entry.getValue().nodeType == ASTNode.COMPILATION_UNIT) {
				return TreeNode.create(entry.getKey(), entry.getValue()
						.nChildProperties());
			}
		}
		throw new IllegalStateException(
				"A compilation unit must have been here...");
	}

	@Override
	public ITokenizer getTokenizer() {
		return new JavaTokenizer();
	}

	/**
	 * Get the tree from a given ASTNode
	 *
	 * @param node
	 * @return
	 */
	public abstract TreeNode<Integer> getTree(final ASTNode node);

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.grammar.tree.ITreeExtractor#getTree(java.io.File)
	 */
	@Override
	public TreeNode<Integer> getTree(final File f) throws IOException {
		final JavaASTExtractor astExtractor = new JavaASTExtractor(false);
		final ASTNode u = astExtractor.getAST(f);
		return getTree(u);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.grammar.tree.ITreeExtractor#getTree(java.lang.String)
	 */
	@Override
	public TreeNode<Integer> getTree(final String code,
			final ParseType parseType) {
		final JavaASTExtractor astExtractor = new JavaASTExtractor(false);
		final ASTNode u = astExtractor.getAST(code, parseType);
		return getTree(u);
	}

	/**
	 * Return the tree printer functor for this extractor.
	 *
	 * @return
	 */
	public Function<TreeNode<Integer>, String> getTreePrinter() {
		return new NodePrinter();
	}

	/**
	 * @param buf
	 * @param intTree
	 */
	public void printMultinode(final StringBuffer buf,
			final TreeNode<Integer> intTree) {
		if (intTree.isLeaf()) {
			return;
		}
		for (int i = 0; i < intTree.getChildrenByProperty().get(0).size(); i++) {
			buf.append(getASTFromTree(intTree.getChild(i, 0)));
			buf.append(" ");
		}

		if (intTree.getChildrenByProperty().get(1).isEmpty()) {
			return;
		}
		final TreeNode<Integer> next = intTree.getChild(0, 1);
		if (getSymbol(next.getData()).nodeType == AstNodeSymbol.MULTI_NODE) {
			printMultinode(buf, next);
		} else {
			buf.append(getASTFromTree(next));
		}
	}

}
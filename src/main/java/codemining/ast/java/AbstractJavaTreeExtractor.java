package codemining.ast.java;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.eclipse.jdt.core.dom.ASTNode;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.collect.BiMap;

import codemining.ast.AbstractTreeExtractor;
import codemining.ast.AstNodeSymbol;
import codemining.ast.TreeNode;
import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.ParseType;

/**
 * An abstract class representing the conversion of Eclipse's ASTNode to
 * ASTNodeSymbols and trees. It also includes the alphabet. Thread safe.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
@DefaultSerializer(JavaSerializer.class)
public abstract class AbstractJavaTreeExtractor extends AbstractTreeExtractor {

	private static final long serialVersionUID = -4515326266227881706L;

	public static final Function<Integer, String> JAVA_NODETYPE_CONVERTER = (Function<Integer, String> & Serializable) nodeType -> ASTNode
			.nodeClassForType(nodeType).getSimpleName();

	/**
	 * A node printer using the symbols.
	 */
	private final TreeToString javaNodeToString = node -> getSymbol(node.getData()).toString(JAVA_NODETYPE_CONVERTER);

	public AbstractJavaTreeExtractor() {
		super();
	}

	protected AbstractJavaTreeExtractor(final BiMap<Integer, AstNodeSymbol> alphabet) {
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
	public String getCodeFromTree(final TreeNode<Integer> tree) {
		return getASTFromTree(tree).toString();
	}

	@Override
	public TreeNode<Integer> getKeyForCompilationUnit() {
		for (final Entry<Integer, AstNodeSymbol> entry : nodeAlphabet.entrySet()) {
			if (entry.getValue().nodeType == ASTNode.COMPILATION_UNIT) {
				return TreeNode.create(entry.getKey(), entry.getValue().nChildProperties());
			}
		}
		throw new IllegalStateException("A compilation unit must have been here...");
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
	public TreeNode<Integer> getTree(final String code, final ParseType parseType) {
		final JavaASTExtractor astExtractor = new JavaASTExtractor(false);
		final ASTNode u = astExtractor.getAST(code, parseType);
		return getTree(u);
	}

	/**
	 * Return a map between the Eclipse ASTNodes and the TreeNodes. This may be
	 * useful for looking up patterns in a reverse order.
	 *
	 * @param node
	 * @return
	 */
	public abstract Map<ASTNode, TreeNode<Integer>> getTreeMap(final ASTNode node);

	/**
	 * Return the tree printer functor for this extractor.
	 *
	 * @return
	 */
	@Override
	public TreeToString getTreePrinter() {
		return javaNodeToString;
	}

}
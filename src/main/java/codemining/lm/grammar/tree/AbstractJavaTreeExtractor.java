package codemining.lm.grammar.tree;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map.Entry;

import org.eclipse.jdt.core.dom.ASTNode;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.ParseType;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.base.Function;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;

/**
 * An abstract class representing the conversion of Eclipse's ASTNode to
 * ASTNodeSymbols and trees. It also includes the alphabet. Thread safe.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
@DefaultSerializer(JavaSerializer.class)
public abstract class AbstractJavaTreeExtractor implements Serializable,
		ITreeExtractor<Integer> {

	/**
	 * A node printer using the symbols.
	 */
	private class NodePrinter implements Function<TreeNode<Integer>, String> {

		@Override
		public String apply(final TreeNode<Integer> node) {
			return getSymbol(node.getData()).toString();
		}

	}

	private static final long serialVersionUID = -9142080646326853618L;

	private int nextSymbolId = 0;

	/**
	 * The symbol map.
	 */
	protected final BiMap<Integer, ASTNodeSymbol> nodeAlphabet;

	public AbstractJavaTreeExtractor() {
		final BiMap<Integer, ASTNodeSymbol> base = HashBiMap.create();
		nodeAlphabet = Maps.synchronizedBiMap(base);
	}

	protected AbstractJavaTreeExtractor(
			final BiMap<Integer, ASTNodeSymbol> alphabet) {
		nodeAlphabet = alphabet;
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
		for (final Entry<Integer, ASTNodeSymbol> entry : nodeAlphabet
				.entrySet()) {
			if (entry.getValue().nodeType == ASTNode.COMPILATION_UNIT) {
				return TreeNode.create(entry.getKey(), entry.getValue()
						.nChildProperties());
			}
		}
		throw new IllegalStateException(
				"A compilation unit must have been here...");
	}

	public final BiMap<Integer, ASTNodeSymbol> getNodeAlphabet() {
		return nodeAlphabet;
	}

	/**
	 * Return the id of the symbol, or create a new one. This will lock the
	 * symbol and thus the id will remain right.
	 * 
	 * @param symbol
	 * @return
	 */
	@Override
	public synchronized int getOrAddSymbolId(final ASTNodeSymbol symbol) {
		final Integer id = nodeAlphabet.inverse().get(symbol);
		if (id != null) {
			return id;
		} else {
			symbol.lockFromChanges();
			final int currentSymboId = nextSymbolId;
			nextSymbolId++;
			nodeAlphabet.put(currentSymboId, symbol);
			return currentSymboId;
		}
	}

	/**
	 * Return the symbol for the given key. Null if there is no symbol for the
	 * given key.
	 * 
	 * @param key
	 * @return
	 */
	@Override
	public ASTNodeSymbol getSymbol(final Integer key) {
		return nodeAlphabet.get(key);
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
	 * @param javaFormattedTSGrammar
	 *            TODO
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
		if (getSymbol(next.getData()).nodeType == ASTNodeSymbol.MULTI_NODE) {
			printMultinode(buf, next);
		} else {
			buf.append(getASTFromTree(next));
		}
	}

}
package codemining.lm.grammar.tree;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import codemining.languagetools.ITokenizer;
import codemining.languagetools.ParseType;

import com.google.common.base.Function;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;

/**
 * An abstract class for extracting tree nodes from code.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public abstract class AbstractTreeExtractor implements Serializable {

	private static final long serialVersionUID = -1685391461506804381L;

	private int nextSymbolId = 0;

	/**
	 * The symbol map.
	 */
	protected final BiMap<Integer, AstNodeSymbol> nodeAlphabet;

	public AbstractTreeExtractor() {
		final BiMap<Integer, AstNodeSymbol> base = HashBiMap.create();
		nodeAlphabet = Maps.synchronizedBiMap(base);
	}

	protected AbstractTreeExtractor(final BiMap<Integer, AstNodeSymbol> alphabet) {
		nodeAlphabet = alphabet;
	}

	/**
	 * Get the code representation of the tree node. This involves getting the
	 * language sepecific AST and converting it to string.
	 *
	 * @param tree
	 * @return
	 */
	public abstract String getCodeFromTree(final TreeNode<Integer> tree);

	/**
	 * Return the node representing the compilation unit.
	 *
	 * @return
	 */
	public abstract TreeNode<Integer> getKeyForCompilationUnit();

	public final BiMap<Integer, AstNodeSymbol> getNodeAlphabet() {
		return nodeAlphabet;
	}

	/**
	 * Return the id of the symbol, or create a new one. This will lock the
	 * symbol and thus the id will remain right.
	 *
	 * @param symbol
	 * @return
	 */
	public synchronized int getOrAddSymbolId(final AstNodeSymbol symbol) {
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
	public AstNodeSymbol getSymbol(final int key) {
		return nodeAlphabet.get(key);
	}

	/**
	 * Return the tokenizer of this tree extractor.
	 *
	 * @return
	 */
	public abstract ITokenizer getTokenizer();

	/**
	 * Return a tree from the given CompilationUnit in the file.
	 *
	 * @param f
	 * @return
	 * @throws IOException
	 */
	public abstract TreeNode<Integer> getTree(File f) throws IOException;

	/**
	 * Return a tree from the given code.
	 *
	 * @param code
	 * @param parseType
	 * @return
	 */
	public abstract TreeNode<Integer> getTree(String code, ParseType parseType);

	/**
	 * Convert a node (symbol) into its string representation. This may (and
	 * will) depend on the language.
	 *
	 * @return
	 */
	public abstract Function<TreeNode<Integer>, String> getTreePrinter();

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
			buf.append(getCodeFromTree(intTree.getChild(i, 0)));
			buf.append(" ");
		}

		if (intTree.getChildrenByProperty().get(1).isEmpty()) {
			return;
		}
		final TreeNode<Integer> next = intTree.getChild(0, 1);
		if (getSymbol(next.getData()).nodeType == AstNodeSymbol.MULTI_NODE) {
			printMultinode(buf, next);
		} else {
			buf.append(getCodeFromTree(next));
		}
	}

}
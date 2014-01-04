package codemining.lm.grammar.tree;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import codemining.languagetools.ITokenizer;
import codemining.languagetools.ParseKind;

/**
 * A generic interface for extracting tree nodes from code.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public interface ITreeExtractor<T extends Serializable> {

	/**
	 * Return the node representing the compilation unit.
	 * 
	 * @return
	 */
	public TreeNode<Integer> getKeyForCompilationUnit();

	/**
	 * Create a symbol for the given AST node symbol.
	 * 
	 * @param symbol
	 * @return
	 */
	public int getOrAddSymbolId(ASTNodeSymbol symbol);

	/**
	 * Return the ASTNodeSymbol for the given node id.
	 * 
	 * @param node
	 * @return
	 */
	public ASTNodeSymbol getSymbol(final T node);

	/**
	 * Return the tokenizer of this tree extractor.
	 * 
	 * @return
	 */
	public ITokenizer getTokenizer();

	/**
	 * Return a tree from the given CompilationUnit in the file.
	 * 
	 * @param f
	 * @return
	 * @throws IOException
	 */
	public TreeNode<T> getTree(File f) throws IOException;

	/**
	 * Return a tree from the given code.
	 * 
	 * @param fileContent
	 * @param parseKind
	 * @return
	 */
	public TreeNode<T> getTree(String fileContent, ParseKind parseKind);

}
/**
 * 
 */
package codemining.lm.grammar.tsg;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.apache.commons.lang.NotImplementedException;

import codemining.languagetools.ITokenizer;
import codemining.languagetools.ParseType;
import codemining.lm.ILanguageModel;
import codemining.lm.grammar.tree.TreeNode;

/**
 * A TSG-based language model.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class TsgLM implements ILanguageModel {

	private static final long serialVersionUID = 3924904324373082051L;

	final TSGrammar<TSGNode> grammar;

	private final ITokenizer tokenizer;

	public TsgLM(final TSGrammar<TSGNode> grammar) {
		this.grammar = grammar;
		tokenizer = grammar.getTreeExtractor().getTokenizer();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.ILanguageModel#getAbsoluteEntropy(java.io.File)
	 */
	@Override
	public double getAbsoluteEntropy(final File file) throws IOException {
		final TreeProbabilityComputer<TSGNode> probComputer = new TreeProbabilityComputer<TSGNode>(
				grammar, true, TreeProbabilityComputer.TSGNODE_MATCHER);
		final TreeNode<Integer> tree = grammar.getTreeExtractor().getTree(file);
		final TreeNode<TSGNode> tsgTree = TSGNode.convertTree(tree, 0);
		return probComputer.getLog2ProbabilityOf(tsgTree);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.ILanguageModel#getAbsoluteEntropy(java.lang.String)
	 */
	@Override
	public double getAbsoluteEntropy(final String fileContent) {
		final TreeProbabilityComputer<TSGNode> probComputer = new TreeProbabilityComputer<TSGNode>(
				grammar, true, TreeProbabilityComputer.TSGNODE_MATCHER);
		final TreeNode<Integer> tree = grammar.getTreeExtractor().getTree(
				fileContent, ParseType.COMPILATION_UNIT);
		final TreeNode<TSGNode> tsgTree = TSGNode.convertTree(tree, 0);
		return probComputer.getLog2ProbabilityOf(tsgTree);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.ILanguageModel#getExtrinsticEntropy(java.io.File)
	 */
	@Override
	public double getExtrinsticEntropy(final File file) throws IOException {
		final String fileContent = FileUtils.readFileToString(file);
		return getExtrinsticEntropy(fileContent);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.ILanguageModel#getExtrinsticEntropy(java.lang.String)
	 */
	@Override
	public double getExtrinsticEntropy(final String fileContent) {
		final char[] code = fileContent.toCharArray();
		if (code.length == 0) {
			return 0;
		}
		final List<String> tokens = tokenizer.tokenListFromCode(code);

		if (tokens.isEmpty()) {
			return 0;
		}

		final double crossEntropy = getAbsoluteEntropy(fileContent)
				/ tokens.size();
		checkArgument(!Double.isNaN(crossEntropy));
		return crossEntropy;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.ILanguageModel#getImmutableVersion()
	 */
	@Override
	public ILanguageModel getImmutableVersion() {
		return this; // TODO
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.ILanguageModel#modelledFilesFilter()
	 */
	@Override
	public AbstractFileFilter modelledFilesFilter() {
		return tokenizer.getFileFilter();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.ILanguageModel#trainIncrementalModel(java.util.Collection)
	 */
	@Override
	public void trainIncrementalModel(final Collection<File> files)
			throws IOException {
		throw new NotImplementedException(
				"TSG language model cannot be trained incrementally");

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.ILanguageModel#trainModel(java.util.Collection)
	 */
	@Override
	public void trainModel(final Collection<File> files) throws IOException {
		throw new NotImplementedException(
				"TSG Language Model is not trained from this interface. Use a sampler instead.");
	}

}

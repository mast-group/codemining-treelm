package codemining.lm.tsg.tui;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.ast.AbstractTreeExtractor;
import codemining.ast.TreeNode;
import codemining.languagetools.ITokenizer;
import codemining.lm.tsg.TSGNode;
import codemining.lm.tsg.TSGrammar;
import codemining.lm.tsg.TreeProbabilityComputer;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * Compute the TSG entropy and cross-entropy of a set of file.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class TsgEntropy {

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 2) {
			System.err.println("Usage <tsg> <directory>");
			System.exit(-1);
		}

		final File directory = new File(args[1]);

		final Collection<File> allFiles = FileUtils.listFiles(directory,
				new RegexFileFilter(".*\\.java$"),
				DirectoryFileFilter.DIRECTORY);

		final TSGrammar<TSGNode> grammar = (TSGrammar<TSGNode>) Serializer
				.getSerializer().deserializeFrom(args[0]);

		final AbstractTreeExtractor treeFormat = grammar.getTreeExtractor();

		System.out.println("filename,entropy,cross-entropy");
		for (final File f : allFiles) {
			try {
				final TreeNode<Integer> intTree = treeFormat.getTree(f);
				final TreeNode<TSGNode> tsgTree = TSGNode.convertTree(intTree,
						0);

				final ITokenizer tokenizer = treeFormat.getTokenizer();
				final List<String> fileTokens = tokenizer
						.tokenListFromCode(FileUtils.readFileToString(f)
								.toCharArray());

				final TreeProbabilityComputer<TSGNode> probabilityComputer = new TreeProbabilityComputer<TSGNode>(
						grammar, false, TreeProbabilityComputer.TSGNODE_MATCHER);
				final double probability = probabilityComputer
						.getLog2ProbabilityOf(tsgTree);

				final double crossEntropy = probability / fileTokens.size();
				System.out.println(f.toString() + "," + probability + ","
						+ crossEntropy);
			} catch (final IOException e) {
				LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
			}
		}

	}

	private static final Logger LOGGER = Logger.getLogger(TsgEntropy.class
			.getName());

	private TsgEntropy() {
	}

}

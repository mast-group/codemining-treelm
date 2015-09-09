/**
 * 
 */
package codemining.lm.cfg.tui;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.ast.java.BinaryJavaAstTreeExtractor;
import codemining.ast.java.ParentTypeAnnotatedJavaAstExtractor;
import codemining.lm.cfg.ContextFreeGrammar;
import codemining.util.serialization.Serializer;

/**
 * @author Miltos Allamanis <m.allamanis@sms.ed.ac.uk>
 * 
 */
public final class JavaCFGrammarLMBuilder {

	/**
	 * The logger.
	 */
	private static final Logger LOGGER = Logger
			.getLogger(JavaCFGrammarLMBuilder.class.getName());

	/**
	 * Main to create a ruleset from a set of files.
	 * 
	 * @param args
	 *            <directory> <grammarFile> <extractorClass>
	 * @throws IOException
	 *             when a file is not found
	 * @throws ClassNotFoundException
	 *             on wrong input
	 * @throws IllegalAccessException
	 *             on wrong input
	 * @throws InstantiationException
	 *             on wrong input
	 */
	public static void main(final String[] args) throws IOException,
			ClassNotFoundException, InstantiationException,
			IllegalAccessException {

		if (args.length != 2) {
			System.err.println("Usage <directory> <grammarFile>");
			return;
		}

		try {
			final ContextFreeGrammar glm = new ContextFreeGrammar(
					new BinaryJavaAstTreeExtractor(
							new ParentTypeAnnotatedJavaAstExtractor()));
			final Collection<File> files = FileUtils.listFiles(
					new File(args[0]), glm.modelledFilesFilter(),
					DirectoryFileFilter.DIRECTORY);
			glm.trainModel(files);
			Serializer.getSerializer().serialize(glm, args[1]);
		} catch (Exception e) {
			LOGGER.severe(ExceptionUtils.getStackTrace(e));
		}
	}

	/**
	 * Default constructor. Nobody uses it.
	 */
	private JavaCFGrammarLMBuilder() {
	}
}

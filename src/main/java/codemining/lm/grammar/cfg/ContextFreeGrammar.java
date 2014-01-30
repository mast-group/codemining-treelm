/**
 * 
 */
package codemining.lm.grammar.cfg;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.languagetools.ParseType;
import codemining.lm.grammar.tree.ITreeExtractor;
import codemining.util.SettingsLoader;
import codemining.util.parallel.ParallelThreadPool;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;

/**
 * A context-free grammar language model.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
@DefaultSerializer(JavaSerializer.class)
public class ContextFreeGrammar extends AbstractContextFreeGrammar {

	/**
	 * A runnable to calculate asynchronously the ASTs and securely count them
	 * in the grammar.
	 * 
	 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
	 * 
	 */
	private final class ASTExtractionRunnable implements Runnable {

		/**
		 * The source file from which the AST will be extracted.
		 */
		private final File sourceFile;

		/**
		 * Constructor.
		 * 
		 * @param file
		 *            the source file from which to extract the grammar.
		 * @param rules
		 *            the ruleset where the rules will be added.
		 * @param grammarProducer
		 *            the grammar format that will produce the rules.
		 */
		public ASTExtractionRunnable(final File file) {
			sourceFile = file;
		}

		@Override
		public void run() {
			try {
				addGrammarRulesFromFile(sourceFile, grammar);
			} catch (final IOException e) {
				LOGGER.warning("Failed to get AST from "
						+ sourceFile.getAbsolutePath() + " "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}

	}

	public static final int CLEAN_THRESHOLD = (int) SettingsLoader
			.getNumericSetting("CleanCountThreshold", 1);

	private static final Logger LOGGER = Logger
			.getLogger(ContextFreeGrammar.class.getName());

	private static final long serialVersionUID = -7892945140311811861L;

	public ContextFreeGrammar(final ITreeExtractor<Integer> treeExtractor) {
		super(treeExtractor, Maps
				.<Integer, Multiset<NodeConsequent>> newConcurrentMap());
	}

	/**
	 * Get the grammar rules from a file.
	 * 
	 * @param sourceFile
	 * @param grammarToAdd
	 * @throws IOException
	 */
	public void addGrammarRulesFromFile(final File sourceFile,
			final Map<Integer, Multiset<NodeConsequent>> grammarToAdd)
			throws IOException {
		final String code = FileUtils.readFileToString(sourceFile);
		addGrammarRulesFromCode(code, grammarToAdd, ParseType.COMPILATION_UNIT);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * uk.ac.ed.inf.languagemodels.ILanguageModel#trainIncrementalModel(java
	 * .io.File)
	 */
	@Override
	public void trainIncrementalModel(final Collection<File> files)
			throws IOException {
		throw new UnsupportedOperationException(
				"CFG cannot be incrementally trained");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see uk.ac.ed.inf.languagemodels.ILanguageModel#trainModel(java.io.File)
	 */
	@Override
	public void trainModel(final Collection<File> trainingFiles)
			throws IOException {

		final ParallelThreadPool ptp = new ParallelThreadPool();

		for (final File file : trainingFiles) {
			ptp.pushTask(new ASTExtractionRunnable(file));
		}

		ptp.waitForTermination();
	}
}

/**
 *
 */
package codemining.lm.grammar.tsg.tui.java;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.lm.grammar.java.ast.AbstractJavaTreeExtractor;
import codemining.lm.grammar.java.ast.BinaryJavaAstTreeExtractor;
import codemining.lm.grammar.java.ast.JavaAstTreeExtractor;
import codemining.lm.grammar.java.ast.TempletizedJavaTreeExtractor;
import codemining.lm.grammar.java.ast.VariableTypeJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.samplers.AbstractTSGSampler;
import codemining.lm.grammar.tsg.samplers.CollapsedGibbsSampler;
import codemining.lm.grammar.tsg.samplers.TempletizedCollapsedGibbsSampler;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class SampleTSG {

	/**
	 * @param args
	 * @throws IOException
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws IOException,
	SerializationException {
		if (args.length != 3) {
			System.err
			.println("Usage <TrainingDir> normal|binary|binary-metavariables|metavariables|variables <#iterations>");
			return;
		}
		final int nIterations = Integer.parseInt(args[2]);

		final File samplerCheckpoint = new File("tsgSampler.ser");
		final CollapsedGibbsSampler sampler;

		if (samplerCheckpoint.exists()) {
			sampler = (CollapsedGibbsSampler) Serializer.getSerializer()
					.deserializeFrom("tsgSampler.ser");
			LOGGER.info("Resuming sampling");

		} else {

			final AbstractJavaTreeExtractor format;
			if (args[1].equals("normal")) {
				format = new JavaAstTreeExtractor();

				sampler = new CollapsedGibbsSampler(20, 10,
						new JavaFormattedTSGrammar(format),
						new JavaFormattedTSGrammar(format));
			} else if (args[1].equals("binary")) {
				format = new BinaryJavaAstTreeExtractor(
						new JavaAstTreeExtractor());

				sampler = new CollapsedGibbsSampler(20, 10,
						new JavaFormattedTSGrammar(format),
						new JavaFormattedTSGrammar(format));
			} else if (args[1].equals("binary-metavariables")) {
				format = new BinaryJavaAstTreeExtractor(
						new TempletizedJavaTreeExtractor());
				sampler = new TempletizedCollapsedGibbsSampler(20, 10, format);
			} else if (args[1].equals("metavariables")) {
				format = new TempletizedJavaTreeExtractor();
				sampler = new TempletizedCollapsedGibbsSampler(20, 10, format);
			} else if (args[1].equals("variables")) {
				format = new VariableTypeJavaTreeExtractor();
				sampler = new CollapsedGibbsSampler(20, 10,
						new JavaFormattedTSGrammar(format),
						new JavaFormattedTSGrammar(format));
			} else {
				throw new IllegalArgumentException("Unrecognizable parameter "
						+ args[1]);
			}
			final double percentRootsInit = .9;
			int nFiles = 0;
			int nNodes = 0;
			for (final File fi : FileUtils.listFiles(new File(args[0]),
					new RegexFileFilter(".*\\.java$"),
					DirectoryFileFilter.DIRECTORY)) {
				try {
					final TreeNode<TSGNode> ast = TSGNode.convertTree(
							format.getTree(fi), percentRootsInit);
					nNodes += ast.getTreeSize();
					nFiles++;
					sampler.addTree(ast);
				} catch (final Exception e) {
					LOGGER.warning("Failed to get AST for "
							+ fi.getAbsolutePath() + " "
							+ ExceptionUtils.getFullStackTrace(e));
				}
			}
			LOGGER.info("Loaded " + nFiles + " files containing " + nNodes
					+ " nodes");
			sampler.lockSamplerData();
		}

		final AtomicBoolean finished = new AtomicBoolean(false);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				int i = 0;
				while (!finished.get() && i < 1000) {
					try {
						Thread.sleep(500);
						i++;
					} catch (final InterruptedException e) {
						LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
					}
				}
			}

		});

		final int nItererationCompleted = sampler.performSampling(nIterations);

		final JavaFormattedTSGrammar grammarToUse;
		if (nItererationCompleted >= nIterations) {
			LOGGER.info("Sampling complete. Outputing burnin grammar...");
			grammarToUse = (JavaFormattedTSGrammar) sampler.getBurnInGrammar();
		} else {
			LOGGER.warning("Sampling not complete. Outputing sample grammar...");
			grammarToUse = (JavaFormattedTSGrammar) sampler.getSampleGrammar();
		}
		try {
			Serializer.getSerializer().serialize(grammarToUse, "tsg.ser");
		} catch (final Throwable e) {
			LOGGER.severe("Failed to serialize grammar: "
					+ ExceptionUtils.getFullStackTrace(e));
		}

		try {
			Serializer.getSerializer().serialize(sampler,
					"tsgSamplerCheckpoint.ser");
		} catch (final Throwable e) {
			LOGGER.severe("Failed to checkpoint sampler: "
					+ ExceptionUtils.getFullStackTrace(e));
		}

		// sampler.pruneNonSurprisingRules(1);
		sampler.pruneRareTrees((int) (AbstractTSGSampler.BURN_IN_PCT * nIterations) - 10);
		System.out.println(grammarToUse.toString());
		finished.set(true); // we have finished and thus the shutdown hook can
		// now stop waiting for us.
	}

	private static final Logger LOGGER = Logger.getLogger(SampleTSG.class
			.getName());
}

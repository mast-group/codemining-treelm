package codemining.lm.grammar.tsg.samplers.tui;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.math.RandomUtils;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.lm.grammar.java.ast.BinaryJavaAstTreeExtractor;
import codemining.lm.grammar.java.ast.JavaAstTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.samplers.AbstractTSGSampler;
import codemining.lm.grammar.tsg.samplers.blocked.BlockCollapsedGibbsSampler;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

public class SnippetTsg {

	private static final Logger LOGGER = Logger.getLogger(SnippetTsg.class
			.getName());

	public static void main(final String[] args) throws SerializationException {
		if (args.length != 1) {
			System.err.println("Usage <nIterations>");
			System.exit(-1);
		}
		final int nIterations = Integer.parseInt(args[0]);
		final List<String> codeSnippets = (List<String>) Serializer
				.getSerializer().deserializeFrom("soSnippets.ser");

		final BinaryJavaAstTreeExtractor format = new BinaryJavaAstTreeExtractor(
				new JavaAstTreeExtractor());
		final BlockCollapsedGibbsSampler sampler = new BlockCollapsedGibbsSampler(
				100, 10, new JavaFormattedTSGrammar(format),
				new JavaFormattedTSGrammar(format));

		final JavaASTExtractor ex = new JavaASTExtractor(false);

		int nNodes = 0;
		int nSnippets = 0;
		for (final String snippet : codeSnippets) {
			if (RandomUtils.nextDouble() > .1) {
				continue;
			}
			try {
				final TreeNode<TSGNode> ast = TSGNode.convertTree(
						format.getTree(ex.getBestEffortAstNode(snippet)), .5);
				nNodes += ast.getTreeSize();
				nSnippets++;
				sampler.addTree(ast);
			} catch (final Exception e) {
				LOGGER.warning("Failed to get AST");
			}
		}
		LOGGER.info("Loaded " + nSnippets + " snippets containing " + nNodes
				+ " nodes");
		sampler.lockSamplerData();

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
			grammarToUse = sampler.getBurnInGrammar();
		} else {
			LOGGER.warning("Sampling not complete. Outputing sample grammar...");
			grammarToUse = sampler.getSampleGrammar();
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
		grammarToUse
				.prune((int) (AbstractTSGSampler.BURN_IN_PCT * nIterations) - 10);
		System.out.println(grammarToUse.toString());
		finished.set(true); // we have finished and thus the shutdown hook can
							// now stop waiting for us.
	}

}

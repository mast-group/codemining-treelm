/**
 *
 */
package codemining.lm.grammar.tsg.tui.java;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jdt.core.dom.ASTNode;

import codemining.java.codeutils.UsagePointExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.lm.grammar.java.ast.AbstractJavaTreeExtractor;
import codemining.lm.grammar.java.ast.BinaryJavaAstTreeExtractor;
import codemining.lm.grammar.java.ast.DelegatedVariableTypeJavaTreeExtractor;
import codemining.lm.grammar.java.ast.JavaAstTreeExtractor;
import codemining.lm.grammar.java.ast.VariableTypeJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.FormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.samplers.AbstractTSGSampler;
import codemining.lm.grammar.tsg.samplers.blocked.BlockCollapsedGibbsSampler;
import codemining.lm.grammar.tsg.samplers.blocked.JavaFilteredBlockCollapsedGibbsSampler;
import codemining.lm.grammar.tsg.samplers.blocked.TreeCorpusFilter;
import codemining.util.SettingsLoader;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * Sample a TSG using a blocked sampler.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class SampleBlockedTSG {

    private static final int TREE_SPLIT_CFG_COUNT = (int) SettingsLoader.getNumericSetting("treeSplitCfgCount", 0);

    /**
     * Filter only the usages of a package.
     */
    private static final String FILTER_ONLY_PACKAGE_USAGES = SettingsLoader.getStringSetting("classFilter", null);
    private static final Logger LOGGER = Logger.getLogger(SampleBlockedTSG.class.getName());

    /**
     * @param args
     * @throws SerializationException
     */
    public static void main(final String[] args) throws SerializationException {
        if (args.length < 5) {
            System.err.println(
                    "Usage <TsgTrainingDir> normal|binary|binaryvariables|variables|binaryvariablesNoAnnotate|delegatedVariableNoAnnotate block|filterblock|icm <alpha> <#iterations> [<CfgExtraTraining>]");
            System.exit(-1);
        }

        final int nIterations = Integer.parseInt(args[4]);
        final double concentrationParameter = Double.parseDouble(args[3]);
        final File samplerCheckpoint = new File("tsgSampler.ser");
        final BlockCollapsedGibbsSampler sampler;

        if (samplerCheckpoint.exists()) {
            sampler = (BlockCollapsedGibbsSampler) Serializer.getSerializer().deserializeFrom("tsgSampler.ser");
            LOGGER.info("Resuming sampling");

        } else {

            final AbstractJavaTreeExtractor format;
            if (args[1].equals("normal")) {
                format = new JavaAstTreeExtractor();
            } else if (args[1].equals("binary")) {
                format = new BinaryJavaAstTreeExtractor(new JavaAstTreeExtractor());
            } else if (args[1].equals("variables")) {
                format = new VariableTypeJavaTreeExtractor();
            } else if (args[1].equals("binaryvariables")) {
                format = new BinaryJavaAstTreeExtractor(new VariableTypeJavaTreeExtractor());
            } else if (args[1].equals("binaryvariablesNoAnnotate")) {
                format = new BinaryJavaAstTreeExtractor(new VariableTypeJavaTreeExtractor(), false);
            } else if (args[1].equals("delegatedVariableNoAnnotate")) {
                format = new BinaryJavaAstTreeExtractor(new DelegatedVariableTypeJavaTreeExtractor(), false);
            } else {
                throw new IllegalArgumentException("Unrecognizable training type parameter " + args[1]);
            }

            if (args[2].equals("block")) {
                sampler = new BlockCollapsedGibbsSampler(100, concentrationParameter, new FormattedTSGrammar(format),
                        new FormattedTSGrammar(format));
            } else if (args[2].equals("filterblock")) {
                sampler = new JavaFilteredBlockCollapsedGibbsSampler(100, concentrationParameter,
                        new FormattedTSGrammar(format), new FormattedTSGrammar(format));
            } else {
                throw new IllegalArgumentException("Unrecognizable training type parameter " + args[2]);
            }

            if (args.length > 5) {
                LOGGER.info("Loading additional CFG prior information from " + args[5]);
                for (final File fi : FileUtils.listFiles(new File(args[5]), new RegexFileFilter(".*\\.java$"),
                        DirectoryFileFilter.DIRECTORY)) {
                    try {
                        final TreeNode<TSGNode> ast = TSGNode.convertTree(format.getTree(fi), 0);
                        sampler.addDataToPrior(ast);
                    } catch (final Exception e) {
                        LOGGER.warning("Failed to get AST for Cfg Prior " + fi.getAbsolutePath() + " "
                                + ExceptionUtils.getFullStackTrace(e));
                    }
                }
            }

            final double percentRootsInit = .9;
            int nFiles = 0;
            int nNodes = 0;
            LOGGER.info("Loading sample trees from  " + args[0]);
            final TreeCorpusFilter filter = new TreeCorpusFilter(format, TREE_SPLIT_CFG_COUNT);
            for (final File fi : FileUtils.listFiles(new File(args[0]), JavaTokenizer.javaCodeFileFilter,
                    DirectoryFileFilter.DIRECTORY)) {
                try {
                    if (FILTER_ONLY_PACKAGE_USAGES == null) {
                        final TreeNode<TSGNode> ast = TSGNode.convertTree(format.getTree(fi), percentRootsInit);
                        nNodes += ast.getTreeSize();
                        filter.addTree(ast);
                    } else {
                        for (final ASTNode tree : UsagePointExtractor.usagePoints(FILTER_ONLY_PACKAGE_USAGES, fi)) {
                            final TreeNode<TSGNode> ast = TSGNode.convertTree(format.getTree(tree), percentRootsInit);
                            nNodes += ast.getTreeSize();
                            filter.addTree(ast);
                        }
                    }
                    nFiles++;
                } catch (final Exception e) {
                    LOGGER.warning("Failed to get AST for " + fi.getAbsolutePath() + " "
                            + ExceptionUtils.getFullStackTrace(e));
                }
            }
            LOGGER.info("Loaded " + nFiles + " files containing " + nNodes + " nodes");
            for (final TreeNode<TSGNode> filteredTree : filter.getFilteredTrees()) {
                sampler.addTree(filteredTree);
            }
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

        final FormattedTSGrammar grammarToUse;
        if (nItererationCompleted >= nIterations) {
            LOGGER.info("Sampling complete. Outputing burnin grammar...");
            grammarToUse = (FormattedTSGrammar) sampler.getBurnInGrammar();
        } else {
            LOGGER.warning("Sampling not complete. Outputing sample grammar...");
            grammarToUse = (FormattedTSGrammar) sampler.getSampleGrammar();
        }
        try {
            Serializer.getSerializer().serialize(grammarToUse, "tsg.ser");
        } catch (final Throwable e) {
            LOGGER.severe("Failed to serialize grammar: " + ExceptionUtils.getFullStackTrace(e));
        }

        try {
            Serializer.getSerializer().serialize(sampler, "tsgSamplerCheckpoint.ser");
        } catch (final Throwable e) {
            LOGGER.severe("Failed to checkpoint sampler: " + ExceptionUtils.getFullStackTrace(e));
        }

        // sampler.pruneNonSurprisingRules(1);
        grammarToUse.prune((int) (AbstractTSGSampler.BURN_IN_PCT * nIterations) - 10);
        System.out.println(grammarToUse.toString());
        finished.set(true); // we have finished and thus the shutdown hook can
        // now stop waiting for us.

    }
}

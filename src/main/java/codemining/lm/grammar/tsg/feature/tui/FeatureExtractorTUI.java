/**
 * 
 */
package codemining.lm.grammar.tsg.feature.tui;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.math.RandomUtils;

import codemining.lm.grammar.java.ast.BinaryEclipseASTTreeExtractor;
import codemining.lm.grammar.java.ast.EclipseASTTreeExtractor;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.feature.FeatureExtractor;
import codemining.lm.grammar.tsg.feature.FeatureExtractor.Sample;
import codemining.lm.grammar.tsg.samplers.CollapsedGibbsSampler;
import codemining.util.serialization.ISerializationStrategy.SerializationException;

import com.google.common.collect.Lists;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedMultiset;
import com.google.common.collect.TreeMultiset;

/**
 * Extract features from TSG patterns.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class FeatureExtractorTUI {

	private static final Logger LOGGER = Logger
			.getLogger(FeatureExtractorTUI.class.getName());

	public static SortedSet<Integer> getBagOfWordFeatures(
			final Collection<Sample> samples, final int countLimit) {
		final SortedMultiset<Integer> contexts = TreeMultiset.create();

		for (final Sample sample : samples) {
			contexts.addAll(sample.previousNodes);
		}

		final SortedSet<Integer> filteredContexts = Sets.newTreeSet();
		for (final Entry<Integer> contextEntry : contexts.entrySet()) {
			if (contextEntry.getCount() > countLimit) {
				filteredContexts.add(contextEntry.getElement());
			}
		}
		return filteredContexts;
	}

	public static void main(final String[] args) throws SerializationException {
		if (args.length != 1) {
			System.err.println("Usage <treeCorpusDir>");
			System.exit(-1);
		}

		final Collection<File> allFiles = FileUtils.listFiles(
				new File(args[0]), new RegexFileFilter(".*\\.java$"),
				DirectoryFileFilter.DIRECTORY);

		// Split train-test 70-30
		final List<File> trainSet = Lists.newArrayList();
		final List<File> testSet = Lists.newArrayList();

		for (final File f : allFiles) {
			if (RandomUtils.nextDouble() < .75) {
				trainSet.add(f);
			} else {
				testSet.add(f);
			}
		}

		// Train...
		final AbstractJavaTreeExtractor format = new BinaryEclipseASTTreeExtractor(
				new EclipseASTTreeExtractor());

		final CollapsedGibbsSampler sampler = new CollapsedGibbsSampler(20, 10,
				new JavaFormattedTSGrammar(format), new JavaFormattedTSGrammar(
						format));

		final double percentRootsInit = .9;
		int nFiles = 0;
		int nNodes = 0;
		for (final File fi : trainSet) {
			try {
				final TreeNode<TSGNode> ast = TSGNode.convertTree(
						format.getTree(fi), percentRootsInit);
				nNodes += TreeNode.getTreeSize(ast);
				nFiles++;
				sampler.addTree(ast);
			} catch (final Exception e) {
				LOGGER.warning("Failed to get AST for " + fi.getAbsolutePath()
						+ " " + ExceptionUtils.getFullStackTrace(e));
			}
		}

		LOGGER.info("Loaded " + nFiles + " files containing " + nNodes
				+ " nodes");
		sampler.lockSamplerData();

		sampler.performSampling(350);

		LOGGER.info("Sampling complete...");

		final FeatureExtractor fEx = new FeatureExtractor(
				sampler.getBurnInGrammar());
		fEx.addTreePatterns();

		System.out.println("=================Train Set====================");
		final Collection<Sample> trainSamples = fEx
				.createSamplesForPatterns(trainSet);
		final SortedSet<Integer> bow = getBagOfWordFeatures(trainSamples, 10);
		toBagOfWordDataset(trainSamples, bow);

		System.out.println("=================Test Set====================");
		final Collection<Sample> testSamples = fEx
				.createSamplesForPatterns(testSet);
		toBagOfWordDataset(testSamples, bow);

		System.out.println("=================Dictionary====================");
		fEx.printTSGDictionary();
	}

	/**
	 * Convert samples to the bag-of-word representation format.
	 * 
	 * @param samples
	 * @param countLimit
	 */
	public static void toBagOfWordDataset(final Collection<Sample> samples,
			final SortedSet<Integer> bowFeatures) {
		// header
		for (final int context : bowFeatures) {
			System.out.print("f" + context + ",");
		}
		System.out.println("pattern");

		// Start printing
		for (final Sample sample : samples) {
			for (final int context : bowFeatures) {
				if (sample.previousNodes.contains(context)) {
					System.out.print("1,");
				} else {
					System.out.print("0,");
				}
			}
			System.out.println(sample.tsgPatternId);
		}
	}

	private FeatureExtractorTUI() {
		// TODO Auto-generated constructor stub
	}
}

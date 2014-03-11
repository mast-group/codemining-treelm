/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jdt.core.dom.CompilationUnit;

import codemining.java.codedata.PackageInfoExtractor;
import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.codeutils.JavaTokenizer;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.pattern.tui.ElementCooccurence.Lift;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;

/**
 * Correlate import statement with patterns.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class ImportPatternCooccurence {

	private static final Logger LOGGER = Logger
			.getLogger(ImportPatternCooccurence.class.getName());

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length < 5) {
			System.err
					.println("Usage <tsg> <minPatternCount> <minPatternSize> <trainPath> <testPath> ");
			System.exit(-1);
		}

		final JavaFormattedTSGrammar grammar = (JavaFormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final AbstractJavaTreeExtractor format = grammar.getJavaTreeExtractor();

		final int minPatternCount = Integer.parseInt(args[1]);
		final int minPatternSize = Integer.parseInt(args[2]);
		final Set<TreeNode<Integer>> patterns = PatternsInCorpus.getPatterns(
				grammar, minPatternCount, minPatternSize);

		final File trainDirectory = new File(args[3]);
		final File testDirectory = new File(args[4]);

		final ImportPatternCooccurence ipc = new ImportPatternCooccurence(
				patterns);

		ipc.removePatternsNotInTest(format, testDirectory);
		LOGGER.info("Training...");
		ipc.train(format, trainDirectory);
		LOGGER.info("Done Training...");
		ipc.printTopPatterns(format);
		System.out.println("===========================================");

	}

	private final BiMap<Integer, TreeNode<Integer>> patternDictionary = HashBiMap
			.create();

	private final ElementCooccurence<String, Integer> patternImportCooccurence = new ElementCooccurence<String, Integer>();

	public ImportPatternCooccurence(final Set<TreeNode<Integer>> patterns) {
		int i = 0;
		for (final TreeNode<Integer> pattern : patterns) {
			patternDictionary.put(i, pattern);
			i++;
		}
	}

	/**
	 * @param fileAst
	 * @return
	 */
	private Set<Integer> patternInFileId(final TreeNode<Integer> fileAst) {
		final Set<TreeNode<Integer>> patternsInFile = PatternsInCorpus
				.getPatternsForTree(fileAst, patternDictionary.values())
				.elementSet();

		final Set<Integer> patternsIdsInFile = Sets.newHashSet();
		for (final TreeNode<Integer> pattern : patternsInFile) {
			patternsIdsInFile.add(patternDictionary.inverse().get(pattern));
		}
		return patternsIdsInFile;
	}

	public void printTopPatterns(final AbstractJavaTreeExtractor format) {
		for (final String packageName : patternImportCooccurence
				.getMostPopularRowFirst().elementSet()) {
			System.out.println("IMPORT: " + packageName);
			System.out.println("=====================================");
			final SortedSet<Lift<String, Integer>> patterns = patternImportCooccurence
					.getCooccuringElementsForRow(packageName);
			int i = 0;
			for (final Lift<String, Integer> pattern : patterns) {
				if (i > 10) {
					break;
				}
				try {
					PrintPatternsFromTsg.printIntTree(format,
							patternDictionary.get(pattern.column));
					System.out.println(String.format("%.2E", pattern.lift));
					System.out.println("Count " + pattern.count);
				} catch (final Throwable e) {
					System.out.println("Failed to print pattern.");
				}
				i++;
				System.out
						.println("---------------------------------------------------");
			}

		}

	}

	/**
	 * Retain only the patterns that appear in the test set.
	 * 
	 * @param format
	 * @param testDirectory
	 */
	private void removePatternsNotInTest(
			final AbstractJavaTreeExtractor format, final File testDirectory) {
		final Collection<File> testFiles = FileUtils
				.listFiles(testDirectory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);
		final Set<Integer> seen = Sets.newHashSet();
		for (final File f : testFiles) {
			try {
				final TreeNode<Integer> fileAst = format.getTree(f);
				final Set<Integer> patternsIdsInFile = patternInFileId(fileAst);
				seen.addAll(patternsIdsInFile);
			} catch (final Exception e) {
				LOGGER.warning("Error in file " + f + " "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}
		final Set<Integer> toRemove = Sets.difference(
				patternDictionary.keySet(), seen).immutableCopy();
		for (final int keyToRemove : toRemove) {
			patternDictionary.remove(keyToRemove);
		}
	}

	private void train(final AbstractJavaTreeExtractor format,
			final File trainDirectory) {
		final Collection<File> testFiles = FileUtils
				.listFiles(trainDirectory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);
		final JavaASTExtractor ex = new JavaASTExtractor(false);
		for (final File f : testFiles) {
			try {
				final CompilationUnit ast = ex.getAST(f);
				final PackageInfoExtractor pie = new PackageInfoExtractor(ast);
				final TreeNode<Integer> fileAst = format.getTree(ast);
				final Set<Integer> patternsIdsInFile = patternInFileId(fileAst);
				patternImportCooccurence.add(Sets.newHashSet(pie.getImports()),
						patternsIdsInFile);
			} catch (final Exception e) {
				LOGGER.warning("Error in file " + f + " "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}
		patternImportCooccurence.prune(5);
	}

}

/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PatternImportCovariance {

	private static final Logger LOGGER = Logger
			.getLogger(PatternImportCovariance.class.getName());

	private static int nTopImports = 500;

	private static int nTopPatterns = 500;

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 5) {
			System.err
					.println("Usage <tsg> <minPatternCount> <minPatternSize> <trainPath> <testPath>");
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

		final PatternImportCovariance pic = new PatternImportCovariance(
				patterns, format);
		pic.removePatternsNotInTest(testDirectory);
		pic.train(trainDirectory);

		pic.printTargetVectors();

	}

	private final BiMap<Integer, TreeNode<Integer>> patternDictionary = HashBiMap
			.create();

	private final AbstractJavaTreeExtractor format;

	private final ElementCooccurence<String, Integer> patternImportCooccurence = new ElementCooccurence<String, Integer>();

	public PatternImportCovariance(final Set<TreeNode<Integer>> patterns,
			final AbstractJavaTreeExtractor format) {
		this.format = format;
		int i = 0;
		for (final TreeNode<Integer> pattern : patterns) {
			patternDictionary.put(i, pattern);
			i++;
		}
	}

	private String getSuperPackage(final String qualifiedName) {
		final String[] pieces = qualifiedName.split("\\.");
		final String packageName = qualifiedName
				.substring(0, qualifiedName.length()
						- pieces[pieces.length - 1].length() - 1);
		checkArgument(qualifiedName.length() > 0,
				"Qualified name of %s not %s", qualifiedName, packageName);
		return packageName;
	}

	private Set<String> parseImports(final List<String> imports) {
		final Set<String> importPackages = Sets.newHashSet();
		for (int i = 0; i < imports.size(); i++) {
			importPackages.add(getSuperPackage(imports.get(i)));
		}
		return importPackages;

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

	private void printTargetVectors() {
		final List<String> topImports = Lists.newArrayList();

		int i = 0;
		System.out.println("Top imports");
		for (final Entry<String> packageNameEntry : Multisets
				.copyHighestCountFirst(
						patternImportCooccurence.getRowMultiset()).entrySet()) {
			if (i > nTopImports || packageNameEntry.getCount() < 5) {
				break;
			}
			topImports.add(packageNameEntry.getElement());

			System.out.println(packageNameEntry.getElement() + ":"
					+ packageNameEntry.getCount());
			i++;
		}
		Collections.sort(topImports);

		final SortedSet<Lift<String, Integer>> topPatternsByLift = Sets
				.newTreeSet();
		for (final String packageName : topImports) {
			topPatternsByLift.addAll(patternImportCooccurence
					.getCooccuringElementsForRow(packageName));
		}

		final Set<Integer> topPatterns = Sets.newHashSet();
		for (final Lift<String, Integer> lift : topPatternsByLift) {
			if (topPatterns.size() > nTopPatterns) {
				break;
			}
			topPatterns.add(lift.column);
		}

		System.out.println("Top patterns");
		for (final int patternId : topPatterns) {
			try {
				PrintPatternsFromTsg.printIntTree(format,
						patternDictionary.get(patternId));
			} catch (final Throwable e) {
				System.out.println("Could not print pattern");
			}
			System.out.println("------------------------------------------");
		}

		for (final int patternId : topPatterns) {
			final List<Double> values = Lists.newArrayList();
			for (final String packageId : topImports) {
				values.add(Math.exp(patternImportCooccurence.getElementLogLift(
						packageId, patternId)));
			}

			for (int j = 0; j < values.size(); j++) {
				System.out.print(String.format("%.5E", values.get(j)) + ",");
			}
			System.out.println();
		}

	}

	/**
	 * Retain only the patterns that appear in the test set.
	 * 
	 * @param format
	 * @param testDirectory
	 */
	private void removePatternsNotInTest(final File testDirectory) {
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
				LOGGER.warning("Error cleaning up in file " + f + " "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}
		final Set<Integer> toRemove = Sets.difference(
				patternDictionary.keySet(), seen).immutableCopy();
		for (final int keyToRemove : toRemove) {
			patternDictionary.remove(keyToRemove);
		}
	}

	private void train(final File trainDirectory) {
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
				final List<String> imports = pie.getImports();
				patternImportCooccurence.add(parseImports(imports),
						patternsIdsInFile);
			} catch (final Exception e) {
				LOGGER.warning("Error training in file " + f + " "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}
		patternImportCooccurence.prune(5);
	}
}

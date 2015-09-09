/**
 *
 */
package codemining.lm.tsg.idioms.tui;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jdt.core.dom.CompilationUnit;

import codemining.ast.TreeNode;
import codemining.ast.java.AbstractJavaTreeExtractor;
import codemining.java.codedata.PackageInfoExtractor;
import codemining.java.codeutils.JavaASTExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.lm.tsg.FormattedTSGrammar;
import codemining.lm.tsg.idioms.PatternCorpus;
import codemining.util.SettingsLoader;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
@DefaultSerializer(JavaSerializer.class)
public class PatternImportCovariance implements Serializable {

	/**
	 * Get the package where each class belongs in
	 *
	 * @param qualifiedName
	 * @return
	 */
	private static String getSuperPackage(final String qualifiedName) {
		final String[] pieces = qualifiedName.split("\\.");
		final String packageName = qualifiedName
				.substring(0, qualifiedName.length()
						- pieces[pieces.length - 1].length() - 1);
		checkArgument(qualifiedName.length() > 0,
				"Qualified name of %s not %s", qualifiedName, packageName);
		return packageName;
	}

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 5) {
			System.err
					.println("Usage <tsg> <minPatternCount> <minPatternSize> <trainPath> <filterPath>");
			System.exit(-1);
		}

		final FormattedTSGrammar grammar = (FormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final AbstractJavaTreeExtractor format = (AbstractJavaTreeExtractor) grammar
				.getTreeExtractor();

		final int minPatternCount = Integer.parseInt(args[1]);
		final int minPatternSize = Integer.parseInt(args[2]);
		final Set<TreeNode<Integer>> patterns = PatternCorpus.getPatternsFrom(
				grammar, minPatternCount, minPatternSize);

		final File trainDirectory = new File(args[3]);
		final File filterDirectory = new File(args[4]);

		final PatternImportCovariance pic = new PatternImportCovariance(
				patterns, format);
		pic.removePatternsNotIn(filterDirectory);
		pic.train(trainDirectory);

		Serializer.getSerializer().serialize(pic, "importCooccurence.ser");

	}

	/**
	 * Parse all the imports to get the package that is "imported".
	 *
	 * @param imports
	 * @return
	 */
	static Set<String> parseImports(final List<String> imports) {
		final Set<String> importPackages = Sets.newHashSet();
		for (int i = 0; i < imports.size(); i++) {
			importPackages.add(getSuperPackage(imports.get(i)));
		}
		return importPackages;

	}

	public static final int cooccuringPairsThreshold = (int) SettingsLoader
			.getNumericSetting("cooccuringPairsThreshold", 5);

	private static final long serialVersionUID = 4891455549393829732L;

	private static final Logger LOGGER = Logger
			.getLogger(PatternImportCovariance.class.getName());

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

	public ElementCooccurence<String, Integer> getElementCooccurence() {
		return patternImportCooccurence;
	}

	public AbstractJavaTreeExtractor getFormat() {
		return format;
	}

	public BiMap<Integer, TreeNode<Integer>> getPatternDictionary() {
		return patternDictionary;
	}

	/**
	 * Return the ids of the patterns that are in this file.
	 *
	 * @param fileAst
	 * @return
	 */
	public Set<Integer> patternInFileId(final TreeNode<Integer> fileAst) {
		final Set<TreeNode<Integer>> patternsInFile = PatternCorpus
				.getPatternsForTree(fileAst, patternDictionary.values())
				.elementSet();

		final Set<Integer> patternsIdsInFile = Sets.newHashSet();
		for (final TreeNode<Integer> pattern : patternsInFile) {
			patternsIdsInFile.add(patternDictionary.inverse().get(pattern));
		}
		return patternsIdsInFile;
	}

	/**
	 * Retain only the patterns that appear in the filter set.
	 *
	 * @param format
	 * @param filterDirectory
	 */
	private void removePatternsNotIn(final File filterDirectory) {
		final Collection<File> filterFiles = FileUtils.listFiles(
				filterDirectory, JavaTokenizer.javaCodeFileFilter,
				DirectoryFileFilter.DIRECTORY);
		final Set<Integer> seen = Sets.newHashSet();
		for (final File f : filterFiles) {
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

	/**
	 * Use the files in the trainset to train the co-occurence weights.
	 *
	 * @param trainDirectory
	 */
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
		patternImportCooccurence.prune(cooccuringPairsThreshold);
	}
}

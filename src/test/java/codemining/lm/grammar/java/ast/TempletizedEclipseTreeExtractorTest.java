/**
 * 
 */
package codemining.lm.grammar.java.ast;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.dom.ASTNode;
import org.junit.Before;
import org.junit.Test;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.languagetools.ParseKind;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class TempletizedEclipseTreeExtractorTest {

	private String classContent;
	private String classContent2;
	private String methodContent;

	private void assertRoundTripConversion(final String code,
			final ParseKind kind, final boolean useComments) {
		final JavaASTExtractor ex = new JavaASTExtractor(false,
				useComments);
		final ASTNode cu = ex.getAST(code, kind);
		final TempletizedEclipseTreeExtractor converter = new TempletizedEclipseTreeExtractor();
		final TreeNode<Integer> treeCu = converter.getTree(cu, useComments);

		final ASTNode reconvertedCu = converter.getASTFromTree(treeCu);

		assertEquals(cu.toString(), reconvertedCu.toString());
	}

	private void assertRoundTripConversionBinarizer(final String code,
			final ParseKind kind, final boolean useComments) {
		final JavaASTExtractor ex = new JavaASTExtractor(false,
				useComments);
		final ASTNode cu = ex.getAST(code, kind);
		final AbstractJavaTreeExtractor converter = new BinaryEclipseASTTreeExtractor(
				new TempletizedEclipseTreeExtractor());
		final TreeNode<Integer> treeCu = converter.getTree(cu);

		final ASTNode reconvertedCu = converter.getASTFromTree(treeCu);

		assertEquals(cu.toString(), reconvertedCu.toString());
	}

	@Test
	public void checkBinarizedCrossConversion() {
		assertRoundTripConversionBinarizer(classContent,
				ParseKind.COMPILATION_UNIT, false);

		assertRoundTripConversionBinarizer(classContent2,
				ParseKind.COMPILATION_UNIT, false);

		assertRoundTripConversionBinarizer(methodContent, ParseKind.METHOD,
				false);
	}

	@Test
	public void checkCrossConversion() {
		assertRoundTripConversion(classContent, ParseKind.COMPILATION_UNIT,
				false);

		assertRoundTripConversion(classContent2, ParseKind.COMPILATION_UNIT,
				false);

		assertRoundTripConversion(methodContent, ParseKind.METHOD, false);
	}

	@Test
	public void checkCrossConversionWithComments() {
		assertRoundTripConversion(classContent, ParseKind.COMPILATION_UNIT,
				true);

		assertRoundTripConversion(classContent2, ParseKind.COMPILATION_UNIT,
				true);

		assertRoundTripConversion(methodContent, ParseKind.METHOD, true);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		classContent = FileUtils.readFileToString(new File(
				TempletizedEclipseTreeExtractorTest.class.getClassLoader()
						.getResource("SampleClass.txt").getFile()));

		classContent2 = FileUtils.readFileToString(new File(
				TempletizedEclipseTreeExtractorTest.class.getClassLoader()
						.getResource("SampleClass2.txt").getFile()));

		methodContent = FileUtils.readFileToString(new File(
				TempletizedEclipseTreeExtractorTest.class.getClassLoader()
						.getResource("SampleMethod.txt").getFile()));
	}

}

/**
 * 
 */
package codemining.lm.grammar.tsg;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.dom.ASTNode;
import org.junit.Before;
import org.junit.Test;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.languagetools.ParseKind;
import codemining.lm.grammar.java.ast.TempletizedJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;

/**
 * @author Miltiadis Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class TempletizedTSGrammarTest {

	private String classContent;
	private String classContent2;
	private String methodContent;

	private void assertReparametrizationUnaffected(final String code,
			final ParseKind kind, final boolean useComments) {
		final JavaASTExtractor ex = new JavaASTExtractor(false,
				useComments);
		final ASTNode cu = ex.getAST(code, kind);
		final TempletizedJavaTreeExtractor converter = new TempletizedJavaTreeExtractor();
		final TempletizedTSGrammar grammar = new TempletizedTSGrammar(converter);
		final TreeNode<Integer> treeCu = converter.getTree(cu, useComments);
		final TreeNode<TSGNode> treeCu2 = TSGNode.convertTree(treeCu, 0);

		assertEquals(TreeNode.getTreeSize(treeCu),
				TreeNode.getTreeSize(treeCu2));
		assertEquals(TreeNode.getTreeSize(treeCu),
				TreeNode.getTreeSize(grammar.reparametrizeTree(treeCu2)));

		assertEquals(
				grammar.reparametrizeTree(grammar.reparametrizeTree(treeCu2)),
				grammar.reparametrizeTree(treeCu2));
	}

	@Test
	public void checkReparametrization() {
		assertReparametrizationUnaffected(classContent,
				ParseKind.COMPILATION_UNIT, false);
		assertReparametrizationUnaffected(classContent2,
				ParseKind.COMPILATION_UNIT, false);
		assertReparametrizationUnaffected(methodContent, ParseKind.METHOD,
				false);
	}

	@Test
	public void checkReparametrizationWithComments() {
		assertReparametrizationUnaffected(classContent,
				ParseKind.COMPILATION_UNIT, true);
		assertReparametrizationUnaffected(classContent2,
				ParseKind.COMPILATION_UNIT, true);
		assertReparametrizationUnaffected(methodContent, ParseKind.METHOD, true);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		classContent = FileUtils.readFileToString(new File(
				TempletizedTSGrammarTest.class.getClassLoader()
						.getResource("SampleClass.txt").getFile()));

		classContent2 = FileUtils.readFileToString(new File(
				TempletizedTSGrammarTest.class.getClassLoader()
						.getResource("SampleClass2.txt").getFile()));

		methodContent = FileUtils.readFileToString(new File(
				TempletizedTSGrammarTest.class.getClassLoader()
						.getResource("SampleMethod.txt").getFile()));
	}
}

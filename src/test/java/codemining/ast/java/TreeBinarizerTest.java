/**
 * 
 */
package codemining.ast.java;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.dom.ASTNode;
import org.junit.Before;
import org.junit.Test;

import codemining.ast.AstNodeSymbol;
import codemining.ast.TreeBinarizer;
import codemining.ast.TreeNode;
import codemining.ast.java.AbstractJavaTreeExtractor;
import codemining.ast.java.BinaryJavaAstTreeExtractor;
import codemining.ast.java.JavaAstTreeExtractor;
import codemining.ast.java.ParentTypeAnnotatedJavaAstExtractor;
import codemining.java.codeutils.JavaASTExtractor;
import codemining.languagetools.ParseType;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class TreeBinarizerTest {

	private String classContent;
	private String classContent2;
	private String methodContent;

	/**
	 * @param code
	 */
	private void assertRoundTripConversion(final String code,
			final ParseType parseType) {
		final JavaASTExtractor ex = new JavaASTExtractor(false);
		final ASTNode cu = ex.getAST(code, parseType);
		final BinaryJavaAstTreeExtractor converter = new BinaryJavaAstTreeExtractor(
				new ParentTypeAnnotatedJavaAstExtractor());
		final TreeNode<Integer> binaryTreeCu = converter.getTree(cu);
		final TreeBinarizer binarizer = converter.getBinarizer();

		final TreeNode<Integer> rebinarizedTree = binarizer
				.binarizeTree(binarizer.debinarize(binaryTreeCu));
		assertEquals(binaryTreeCu, rebinarizedTree);
	}

	public TreeNode<Integer> generateSampleTree() {
		final TreeNode<Integer> root = TreeNode.create(1, 1);
		final TreeNode<Integer> child1 = TreeNode.create(2, 1);
		final TreeNode<Integer> child2 = TreeNode.create(3, 1);

		root.addChildNode(child2, 0);
		root.addChildNode(child1, 0);

		final TreeNode<Integer> grandchild1 = TreeNode.create(4, 1);
		final TreeNode<Integer> grandchild2 = TreeNode.create(5, 0);
		final TreeNode<Integer> grandchild3 = TreeNode.create(6, 0);

		child1.addChildNode(grandchild1, 0);
		child1.addChildNode(grandchild2, 0);
		child1.addChildNode(grandchild3, 0);

		return root;
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		classContent = FileUtils.readFileToString(new File(
				TreeBinarizerTest.class.getClassLoader()
						.getResource("SampleClass.txt").getFile()));

		classContent2 = FileUtils.readFileToString(new File(
				TreeBinarizerTest.class.getClassLoader()
						.getResource("SampleClass2.txt").getFile()));

		methodContent = FileUtils.readFileToString(new File(
				TreeBinarizerTest.class.getClassLoader()
						.getResource("SampleMethod.txt").getFile()));
	}

	@Test
	public void testBinarizationSimple() {
		AbstractJavaTreeExtractor extractor = new JavaAstTreeExtractor();

		for (int i = 0; i < 10; i++) { // Create dummy symbols
			extractor.getOrAddSymbolId(new AstNodeSymbol(i));
		}

		final TreeBinarizer binarizer = new TreeBinarizer(extractor);
		final TreeNode<Integer> binaryTree = binarizer
				.binarizeTree(generateSampleTree());

		assertEquals(generateSampleTree(), binarizer.debinarize(binaryTree));
	}

	@Test
	public void testRoundtrip() {
		assertRoundTripConversion(classContent, ParseType.COMPILATION_UNIT);
		assertRoundTripConversion(classContent2, ParseType.COMPILATION_UNIT);
		assertRoundTripConversion(methodContent, ParseType.METHOD);
	}

}

package codemining.lm.grammar.tsg.samplers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.ITreeSubstitutionGrammar;
import codemining.lm.grammar.tsg.TSGrammar;

public class TSGrammarTest {

	private TreeNode<Integer> generateSampleTree1() {
		final TreeNode<Integer> root = TreeNode.create(1, 3);

		final TreeNode<Integer> child1 = TreeNode.create(2, 1);
		final TreeNode<Integer> child2 = TreeNode.create(3, 2);

		root.addChildNode(child1, 2);
		root.addChildNode(child2, 1);

		final TreeNode<Integer> grandchild1 = TreeNode.create(4, 0);
		final TreeNode<Integer> grandchild2 = TreeNode.create(5, 0);
		final TreeNode<Integer> grandchild3 = TreeNode.create(6, 1);

		child1.addChildNode(grandchild1, 0);
		child2.addChildNode(grandchild2, 1);
		child2.addChildNode(grandchild3, 0);

		return root;
	}

	private TreeNode<Integer> generateSampleTree2() {
		final TreeNode<Integer> root = TreeNode.create(6, 1);

		final TreeNode<Integer> child1 = TreeNode.create(5, 1);
		final TreeNode<Integer> child2 = TreeNode.create(4, 1);

		root.addChildNode(child1, 0);
		root.addChildNode(child2, 0);

		final TreeNode<Integer> grandchild1 = TreeNode.create(3, 1);
		final TreeNode<Integer> grandchild2 = TreeNode.create(2, 2);
		final TreeNode<Integer> grandchild3 = TreeNode.create(1, 3);

		child1.addChildNode(grandchild1, 0);
		child2.addChildNode(grandchild2, 0);
		child2.addChildNode(grandchild3, 0);

		return root;
	}

	private TreeNode<Integer> generateSampleTree3() {
		final TreeNode<Integer> root = TreeNode.create(1, 3);

		final TreeNode<Integer> child1 = TreeNode.create(2, 1);
		final TreeNode<Integer> child2 = TreeNode.create(3, 2);

		root.addChildNode(child1, 2);
		root.addChildNode(child2, 1);

		return root;
	}

	private TreeNode<Integer> generateSampleTree4() {
		final TreeNode<Integer> root = TreeNode.create(1, 3);

		final TreeNode<Integer> child1 = TreeNode.create(58, 10);
		final TreeNode<Integer> child2 = TreeNode.create(10, 0);

		root.addChildNode(child1, 2);
		root.addChildNode(child2, 2);

		return root;
	}

	@Test
	public void testGrammar() {
		final ITreeSubstitutionGrammar<Integer> grammar = new TSGrammar<Integer>();
		grammar.addTree(generateSampleTree1());
		assertEquals(grammar.countTreeOccurences(generateSampleTree1()), 1);
		assertEquals(grammar.countTreesWithRoot(1), 1);
		grammar.addTree(generateSampleTree1());
		assertEquals(grammar.countTreeOccurences(generateSampleTree1()), 2);
		assertEquals(grammar.countTreesWithRoot(1), 2);
		assertEquals(grammar.countTreesWithRoot(6), 0);

		assertEquals(grammar.countTreeOccurences(generateSampleTree2()), 0);
		assertEquals(grammar.countTreeOccurences(generateSampleTree3()), 0);
		assertEquals(grammar.countTreeOccurences(generateSampleTree4()), 0);

		grammar.addTree(generateSampleTree3());
		assertEquals(grammar.countTreeOccurences(generateSampleTree1()), 2);
		assertEquals(grammar.countTreeOccurences(generateSampleTree2()), 0);
		assertEquals(grammar.countTreeOccurences(generateSampleTree3()), 1);
		assertEquals(grammar.countTreeOccurences(generateSampleTree4()), 0);
		assertEquals(grammar.countTreesWithRoot(1), 3);

		grammar.removeTree(generateSampleTree1());
		assertEquals(grammar.countTreeOccurences(generateSampleTree1()), 1);
		assertEquals(grammar.countTreeOccurences(generateSampleTree2()), 0);
		assertEquals(grammar.countTreeOccurences(generateSampleTree3()), 1);
		assertEquals(grammar.countTreeOccurences(generateSampleTree4()), 0);
		assertEquals(grammar.countTreesWithRoot(1), 2);

		assertFalse(grammar.removeTree(generateSampleTree2()));
		assertEquals(grammar.countTreeOccurences(generateSampleTree1()), 1);
		assertEquals(grammar.countTreeOccurences(generateSampleTree2()), 0);
		assertEquals(grammar.countTreeOccurences(generateSampleTree3()), 1);
		assertEquals(grammar.countTreeOccurences(generateSampleTree4()), 0);
		assertEquals(grammar.countTreesWithRoot(1), 2);
	}

}

/**
 * 
 */
package codemining.lm.grammar.tree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class TreeNodeTest {

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testAddingChildren() {
		final TreeNode<Integer> node = TreeNode.create(1, 2);
		node.addChildNode(TreeNode.create(0, 0), 0);
		node.addChildNode(TreeNode.create(1, 0), 0);
		node.addChildNode(TreeNode.create(2, 0), 1);

		assertEquals(node.nProperties(), 2);
		assertEquals(node.getChild(0, 0), TreeNode.create(0, 0));
		assertEquals(node.getChild(0, 0).getData(), (Integer) 0);
		assertEquals(node.getChild(1, 0).getData(), (Integer) 1);
		assertEquals(node.getChild(0, 1).getData(), (Integer) 2);

		assertEquals(TreeNode.getTreeSize(node), 4);
	}

	@Test
	public void testIsLeaf() {
		final TreeNode<Integer> node = TreeNode.create(1, 10);
		assertTrue(node.isLeaf());
		assertEquals(node.nProperties(), 10);
		assertEquals(TreeNode.getTreeSize(node), 1);
	}

	@Test
	public void testPartialMatch() {
		final TreeNode<Integer> root = TreeNode.create(1, 2);
		root.addChildNode(TreeNode.create(0, 0), 0);
		final TreeNode<Integer> c2 = TreeNode.create(1, 2);
		root.addChildNode(c2, 1);
		final TreeNode<Integer> c2_1 = TreeNode.create(2, 1);
		c2.addChildNode(c2_1, 0);
		c2.addChildNode(TreeNode.create(8, 0), 1);
		c2_1.addChildNode(TreeNode.create(4, 0), 0);

		final TreeNode<Integer> root2 = TreeNode.create(1, 2);
		root2.addChildNode(TreeNode.create(0, 0), 0);

		assertTrue(root2.partialMatch(root, false));
		assertFalse(root.partialMatch(root2, false));
		assertTrue(root2.partialMatch(root, true));
		assertFalse(root.partialMatch(root2, true));

		final TreeNode<Integer> c2v2 = TreeNode.create(1, 2);
		root2.addChildNode(c2v2, 1);

		assertTrue(root2.partialMatch(root, false));
		assertFalse(root.partialMatch(root2, false));
		assertTrue(root2.partialMatch(root, true));
		assertFalse(root.partialMatch(root2, true));

		final TreeNode<Integer> c2_1v2 = TreeNode.create(2, 1);
		c2v2.addChildNode(c2_1v2, 0);
		assertTrue(root2.partialMatch(root, false));
		assertFalse(root.partialMatch(root2, false));
		assertTrue(root2.partialMatch(root, true));
		assertFalse(root.partialMatch(root2, true));

		c2_1v2.addChildNode(TreeNode.create(4, 0), 0);
		assertTrue(root2.partialMatch(root, false));
		assertTrue(root2.partialMatch(root, true));
		assertFalse(root.partialMatch(root2, true));
		assertTrue(root2.partialMatch(root, true));

		c2v2.addChildNode(TreeNode.create(8, 0), 1);
		assertTrue(root.partialMatch(root2, false));
		assertTrue(root2.partialMatch(root, false));
		assertTrue(root.partialMatch(root2, true));
		assertTrue(root2.partialMatch(root, true));
	}

}

/**
 * 
 */
package codemining.lm.grammar.tree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import codemining.lm.grammar.tree.TreeNode.NodeParents;

import com.google.common.collect.Lists;

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
	public void testParents() {
		// Construct a tree
		final TreeNode<Integer> node1 = TreeNode.create(1, 2); // 1
		final TreeNode<Integer> node2 = TreeNode.create(2, 2); // -2
		final TreeNode<Integer> node3 = TreeNode.create(3, 2); // --3
		final TreeNode<Integer> node4 = TreeNode.create(4, 2); // --4
		final TreeNode<Integer> node5 = TreeNode.create(5, 2); // ---5
		final TreeNode<Integer> node6 = TreeNode.create(6, 2); // -6
		final TreeNode<Integer> node7 = TreeNode.create(7, 2); // --7

		node1.addChildNode(node2, 0);
		node1.addChildNode(node6, 1);
		node2.addChildNode(node3, 0);
		node2.addChildNode(node4, 0);
		node4.addChildNode(node5, 1);
		node6.addChildNode(node7, 1);

		// Test the route from 5 to 1
		final NodeParents<Integer> route1 = node5.getNodeParents(node1);
		assertEquals(route1.targetNode, node5);
		final List<TreeNode<Integer>> pathFrom5to1 = Lists.newArrayList(node4,
				node2, node1);
		assertEquals(route1.throughNodes, pathFrom5to1);

		final List<Integer> propertyPathFrom5to1 = Lists.newArrayList(1, 0, 0);
		final List<Integer> childPathFrom5to1 = Lists.newArrayList(0, 1, 0);
		assertEquals(route1.nextProperty, propertyPathFrom5to1);
		assertEquals(route1.nextChildNum, childPathFrom5to1);

		// Test the route from 7 to 1
		final NodeParents<Integer> route2 = node7.getNodeParents(node1);
		assertEquals(route2.targetNode, node7);
		final List<TreeNode<Integer>> pathFrom7to1 = Lists.newArrayList(node6,
				node1);
		assertEquals(route2.throughNodes, pathFrom7to1);

		final List<Integer> propertyPathFrom7to1 = Lists.newArrayList(1, 1);
		final List<Integer> childPathFrom7to1 = Lists.newArrayList(0, 0);
		assertEquals(route2.nextProperty, propertyPathFrom7to1);
		assertEquals(route2.nextChildNum, childPathFrom7to1);

		// Test (dummy) route from 1 to 1
		final NodeParents<Integer> route3 = node1.getNodeParents(node1);
		assertEquals(route3.targetNode, node1);
		assertEquals(route3.throughNodes, Collections.emptyList());
		assertEquals(route3.nextProperty, Collections.emptyList());
		assertEquals(route3.nextChildNum, Collections.emptyList());
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
		assertFalse(root2.partialMatch(root, true));
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
		assertFalse(root2.partialMatch(root, true));
		assertFalse(root.partialMatch(root2, true));

		c2_1v2.addChildNode(TreeNode.create(4, 0), 0);
		assertTrue(root2.partialMatch(root, false));
		assertTrue(root2.partialMatch(root, false));
		assertFalse(root.partialMatch(root2, true));
		assertFalse(root2.partialMatch(root, true));

		c2v2.addChildNode(TreeNode.create(8, 0), 1);
		assertTrue(root.partialMatch(root2, false));
		assertTrue(root2.partialMatch(root, false));
		assertTrue(root.partialMatch(root2, true));
		assertTrue(root2.partialMatch(root, true));
	}

}

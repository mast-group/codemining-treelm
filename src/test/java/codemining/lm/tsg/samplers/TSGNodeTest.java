package codemining.lm.tsg.samplers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import codemining.ast.TreeNode;
import codemining.lm.tsg.TSGNode;
import codemining.lm.tsg.samplers.blocked.TreeWithNodeIndex;

public class TSGNodeTest {

	/**
	 * Return a node with structure {1R: {2R: {4}, 3: {5, 6}}}.
	 * 
	 * @return
	 */
	public TreeNode<TSGNode> generateSampleTree() {
		final TSGNode rootNode = new TSGNode(1);
		rootNode.isRoot = true;
		final TreeNode<TSGNode> root = TreeNode.create(rootNode, 2);

		final TSGNode child1Node = new TSGNode(2);
		final TreeNode<TSGNode> child1 = TreeNode.create(child1Node, 1);
		final TSGNode child2Node = new TSGNode(3);
		final TreeNode<TSGNode> child2 = TreeNode.create(child2Node, 1);

		root.addChildNode(child2, 1);
		root.addChildNode(child1, 0);

		final TSGNode grandchild1Node = new TSGNode(4);
		final TreeNode<TSGNode> grandchild1 = TreeNode.create(grandchild1Node,
				1);
		final TSGNode grandchild2Node = new TSGNode(5);
		final TreeNode<TSGNode> grandchild2 = TreeNode.create(grandchild2Node,
				0);
		final TSGNode grandchild3Node = new TSGNode(6);
		final TreeNode<TSGNode> grandchild3 = TreeNode.create(grandchild3Node,
				0);

		child1.addChildNode(grandchild1, 0);
		child2.addChildNode(grandchild2, 0);
		child2.addChildNode(grandchild3, 0);

		return root;
	}

	@Test
	public void getSubtree() {
		final TreeNode<TSGNode> tree1 = generateSampleTree();
		tree1.getChild(0, 0).getData().isRoot = true;

		final TSGNode rootNode = new TSGNode(1);
		rootNode.isRoot = true;
		final TreeNode<TSGNode> root = TreeNode.create(rootNode, 2);

		final TSGNode child1Node = new TSGNode(2);
		final TreeNode<TSGNode> child1 = TreeNode.create(child1Node, 1);
		final TSGNode child2Node = new TSGNode(3);
		final TreeNode<TSGNode> child2 = TreeNode.create(child2Node, 1);

		root.addChildNode(child1, 0);
		root.addChildNode(child2, 1);

		final TSGNode grandchild2Node = new TSGNode(5);
		final TreeNode<TSGNode> grandchild2 = TreeNode.create(grandchild2Node,
				0);
		final TSGNode grandchild3Node = new TSGNode(6);
		final TreeNode<TSGNode> grandchild3 = TreeNode.create(grandchild3Node,
				0);

		child1Node.isRoot = true;
		child2.addChildNode(grandchild2, 0);
		child2.addChildNode(grandchild3, 0);

		final TreeNode<TSGNode> subTree = TSGNode.getSubTreeFromRoot(tree1);
		assertEquals(subTree, root);

	}

	@Test
	public void testConversion() {
		final TreeNode<TSGNode> tree1 = generateSampleTree();
		assertEquals(tree1, generateSampleTree());

		// Construct int tree
		final TreeNode<Integer> root = TreeNode.create(1, 2);
		final TreeNode<Integer> child1 = TreeNode.create(2, 1);
		root.addChildNode(child1, 0);
		final TreeNode<Integer> child2 = TreeNode.create(3, 1);
		root.addChildNode(child2, 1);
		final TreeNode<Integer> grandchild1 = TreeNode.create(4, 1);
		child1.addChildNode(grandchild1, 0);
		final TreeNode<Integer> grandchild2 = TreeNode.create(5, 0);
		final TreeNode<Integer> grandchild3 = TreeNode.create(6, 0);
		child2.addChildNode(grandchild2, 0);
		child2.addChildNode(grandchild3, 0);

		final TreeNode<TSGNode> converted = TSGNode.convertTree(root, 0);
		assertTrue(tree1.equals(converted));

		assertEquals(tree1, TSGNode.getSubTreeFromRoot(tree1));
	}

	@Test
	public void testTreesMatchToRoot() {
		final TreeWithNodeIndex tree1 = TreeWithNodeIndex.generateTree1();
		final TreeWithNodeIndex tree2 = TreeWithNodeIndex.generateTree2();

		assertFalse(TSGNode.treesMatchToRoot(tree1.tree, tree2.tree));

		tree1.nodeIndex.get(3).getData().isRoot = true;
		assertFalse(TSGNode.treesMatchToRoot(tree1.tree, tree2.tree));
		tree2.nodeIndex.get(3).getData().isRoot = true;
		assertTrue(TSGNode.treesMatchToRoot(tree1.tree, tree2.tree));

		tree2.nodeIndex.get(4).getData().isRoot = true;
		assertFalse(TSGNode.treesMatchToRoot(tree1.tree, tree2.tree));
		tree1.nodeIndex.get(4).getData().isRoot = true;
		assertTrue(TSGNode.treesMatchToRoot(tree1.tree, tree2.tree));

		assertTrue(TSGNode.treesMatchToRoot(tree1.nodeIndex.get(2),
				tree2.nodeIndex.get(2)));
		assertTrue(TSGNode.treesMatchToRoot(tree1.nodeIndex.get(4),
				tree2.nodeIndex.get(4)));
	}

}

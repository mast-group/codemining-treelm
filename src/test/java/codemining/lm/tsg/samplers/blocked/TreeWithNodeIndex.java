package codemining.lm.tsg.samplers.blocked;

import java.util.Map;

import codemining.ast.TreeNode;
import codemining.lm.tsg.TSGNode;

import com.google.common.collect.Maps;

public class TreeWithNodeIndex {
	public static TreeWithNodeIndex generateTree1() {
		final TreeWithNodeIndex tree = new TreeWithNodeIndex();
		for (int i = 1; i <= 12; i++) {
			tree.nodeIndex.put(i, TreeNode.create(new TSGNode(i), 2));
		}

		tree.tree = tree.nodeIndex.get(1);
		tree.nodeIndex.get(1).getData().isRoot = true;

		tree.nodeIndex.get(1).addChildNode(tree.nodeIndex.get(2), 0);
		tree.nodeIndex.get(1).addChildNode(tree.nodeIndex.get(3), 0);
		tree.nodeIndex.get(2).addChildNode(tree.nodeIndex.get(4), 0);
		tree.nodeIndex.get(2).addChildNode(tree.nodeIndex.get(5), 0);
		tree.nodeIndex.get(4).addChildNode(tree.nodeIndex.get(6), 0);
		tree.nodeIndex.get(4).addChildNode(tree.nodeIndex.get(7), 0);
		tree.nodeIndex.get(6).addChildNode(tree.nodeIndex.get(9), 1);
		tree.nodeIndex.get(7).addChildNode(tree.nodeIndex.get(10), 0);
		tree.nodeIndex.get(5).addChildNode(tree.nodeIndex.get(8), 0);
		tree.nodeIndex.get(8).addChildNode(tree.nodeIndex.get(11), 0);
		tree.nodeIndex.get(8).addChildNode(tree.nodeIndex.get(12), 0);

		return tree;
	}

	/**
	 * Tree2 is the same as tree1 but with extra nodes. The indexes do not
	 * necessarily refer to the node types.
	 * 
	 * @return
	 */
	public static TreeWithNodeIndex generateTree2() {
		final TreeWithNodeIndex tree = new TreeWithNodeIndex();
		for (int i = 1; i <= 12; i++) {
			tree.nodeIndex.put(i, TreeNode.create(new TSGNode(i), 2));
		}
		tree.nodeIndex.put(13, TreeNode.create(new TSGNode(4), 2));
		tree.nodeIndex.put(14, TreeNode.create(new TSGNode(6), 2));
		tree.nodeIndex.put(15, TreeNode.create(new TSGNode(7), 2));
		tree.nodeIndex.put(16, TreeNode.create(new TSGNode(9), 2));
		tree.nodeIndex.put(17, TreeNode.create(new TSGNode(10), 2));

		tree.tree = tree.nodeIndex.get(1);
		tree.nodeIndex.get(1).getData().isRoot = true;

		tree.nodeIndex.get(1).addChildNode(tree.nodeIndex.get(2), 0);
		tree.nodeIndex.get(1).addChildNode(tree.nodeIndex.get(3), 0);
		tree.nodeIndex.get(2).addChildNode(tree.nodeIndex.get(4), 0);
		tree.nodeIndex.get(2).addChildNode(tree.nodeIndex.get(5), 0);
		tree.nodeIndex.get(4).addChildNode(tree.nodeIndex.get(6), 0);
		tree.nodeIndex.get(4).addChildNode(tree.nodeIndex.get(7), 0);
		tree.nodeIndex.get(6).addChildNode(tree.nodeIndex.get(9), 1);
		tree.nodeIndex.get(7).addChildNode(tree.nodeIndex.get(10), 0);
		tree.nodeIndex.get(5).addChildNode(tree.nodeIndex.get(8), 0);
		tree.nodeIndex.get(8).addChildNode(tree.nodeIndex.get(11), 0);
		tree.nodeIndex.get(8).addChildNode(tree.nodeIndex.get(12), 0);
		tree.nodeIndex.get(3).addChildNode(tree.nodeIndex.get(13), 0);
		tree.nodeIndex.get(13).addChildNode(tree.nodeIndex.get(14), 0);
		tree.nodeIndex.get(13).addChildNode(tree.nodeIndex.get(15), 0);
		tree.nodeIndex.get(14).addChildNode(tree.nodeIndex.get(16), 1);
		tree.nodeIndex.get(15).addChildNode(tree.nodeIndex.get(17), 0);

		return tree;
	}

	public TreeNode<TSGNode> tree;

	public Map<Integer, TreeNode<TSGNode>> nodeIndex = Maps.newTreeMap();
}
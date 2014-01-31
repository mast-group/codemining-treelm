package codemining.lm.grammar.tsg;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.math.RandomUtils;

import codemining.lm.grammar.tree.TreeNode;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * A node for Tree Substitution Grammar that contains the extra information
 * about
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
@DefaultSerializer(JavaSerializer.class)
public class TSGNode implements Serializable {

	/**
	 * A struct class containing from and to pair of nodes to copy.
	 * 
	 */
	public static final class CopyPair {
		public final TreeNode<TSGNode> fromNode;

		public final TreeNode<TSGNode> toNode;

		public CopyPair(final TreeNode<TSGNode> from, final TreeNode<TSGNode> to) {
			fromNode = from;
			toNode = to;
		}
	}

	private static final long serialVersionUID = -6314484338435759026L;

	/**
	 * Wrap TreeNode<Integer> trees in TreeNode<TSGNode> trees.
	 * 
	 * @param tree
	 * @return
	 */
	public static TreeNode<TSGNode> convertTree(final TreeNode<Integer> tree,
			final double percentRootsToIntroduce) {
		final TSGNode rootNode = new TSGNode(tree.getData());
		rootNode.isRoot = true;
		final TreeNode<TSGNode> root = TreeNode.create(rootNode,
				tree.nProperties());
		copyChildren(root, tree, percentRootsToIntroduce);
		return root;
	}

	/**
	 * Copy children doing the transformation. This is the most intensive
	 * function used. Every effort is made to make it heavily optimized!
	 * 
	 * @param toNode
	 * @param fromNode
	 */
	public static void copyChildren(final TreeNode<Integer> toNode,
			final TreeNode<TSGNode> fromNode) {
		final ArrayDeque<TreeNode<Integer>> toStack = new ArrayDeque<TreeNode<Integer>>();
		final ArrayDeque<TreeNode<TSGNode>> fromStack = new ArrayDeque<TreeNode<TSGNode>>();

		toStack.push(toNode);
		fromStack.push(fromNode);

		while (!toStack.isEmpty()) {
			final TreeNode<Integer> currentTo = toStack.pop();
			final TreeNode<TSGNode> currentFrom = fromStack.pop();

			final List<List<TreeNode<TSGNode>>> children = currentFrom
					.getChildrenByProperty();

			final int nChildren = children.size();
			for (int i = 0; i < nChildren; i++) {
				for (final TreeNode<TSGNode> fromChild : children.get(i)) {
					final int toChildData = fromChild.getData().nodeKey;
					final TreeNode<Integer> toChild = TreeNode.create(
							toChildData, fromChild.nProperties());
					currentTo.addChildNode(toChild, i);
					toStack.push(toChild);
					fromStack.push(fromChild);
				}
			}
		}
	}

	/**
	 * Copy children
	 * 
	 * @param toNode
	 * @param fromNode
	 * @param precentRootsToIntroduce
	 */
	public static void copyChildren(final TreeNode<TSGNode> toNode,
			final TreeNode<Integer> fromNode,
			final double precentRootsToIntroduce) {
		final ArrayDeque<TreeNode<TSGNode>> toStack = new ArrayDeque<TreeNode<TSGNode>>();
		final ArrayDeque<TreeNode<Integer>> fromStack = new ArrayDeque<TreeNode<Integer>>();

		toStack.push(toNode);
		fromStack.push(fromNode);

		while (!toStack.isEmpty()) {
			final TreeNode<TSGNode> currentTo = toStack.pop();
			final TreeNode<Integer> currentFrom = fromStack.pop();

			final List<List<TreeNode<Integer>>> children = currentFrom
					.getChildrenByProperty();

			for (int i = 0; i < children.size(); i++) {
				for (final TreeNode<Integer> fromChild : children.get(i)) {
					final TSGNode toChildData = new TSGNode(fromChild.getData());
					if (fromChild.isLeaf()) {
						toChildData.isRoot = false;
					} else {
						toChildData.isRoot = RandomUtils.nextDouble() < precentRootsToIntroduce;
					}
					final TreeNode<TSGNode> toChild = TreeNode.create(
							toChildData, fromChild.nProperties());
					currentTo.addChildNode(toChild, i);
					toStack.push(toChild);
					fromStack.push(fromChild);
				}
			}
		}
	}

	/**
	 * Copy the children (and all (grand+)children) to the given toNode. If
	 * stopOnRoots is given, then copying will stop on roots.
	 * 
	 * @param fromNode
	 * @param toNode
	 * @param stopOnRoots
	 */
	static void copyChildren(final TreeNode<TSGNode> fromNode,
			final TreeNode<TSGNode> toNode, final boolean stopOnRoots) {
		final ArrayDeque<CopyPair> stack = new ArrayDeque<CopyPair>();

		stack.push(new CopyPair(fromNode, toNode));

		while (!stack.isEmpty()) {
			final CopyPair pair = stack.pop();
			final TreeNode<TSGNode> currentFrom = pair.fromNode;
			final TreeNode<TSGNode> currentTo = pair.toNode;

			final List<List<TreeNode<TSGNode>>> children = currentFrom
					.getChildrenByProperty();

			for (int i = 0; i < children.size(); i++) {
				for (final TreeNode<TSGNode> fromChild : children.get(i)) {
					final TSGNode toChildData = new TSGNode(fromChild.getData());
					final TreeNode<TSGNode> toChild = TreeNode.create(
							toChildData, fromChild.nProperties());
					currentTo.addChildNode(toChild, i);
					if (stopOnRoots && toChildData.isRoot) {
						continue;
					}

					stack.push(new CopyPair(fromChild, toChild));
				}
			}
		}
	}

	/**
	 * Return a list of all the rooted trees in this tree.
	 * 
	 * @param tree
	 * @return
	 */
	public static List<TreeNode<TSGNode>> getAllRootsOf(
			final TreeNode<TSGNode> tree) {
		final List<TreeNode<TSGNode>> nodes = Lists.newArrayList();
		final ArrayDeque<TreeNode<TSGNode>> toVisit = new ArrayDeque<TreeNode<TSGNode>>();

		toVisit.push(tree);

		while (!toVisit.isEmpty()) {
			final TreeNode<TSGNode> currentTree = toVisit.pollFirst();

			if (currentTree.getData().isRoot) {
				nodes.add(getSubTreeFromRoot(currentTree));
			}

			for (final List<TreeNode<TSGNode>> childProperties : currentTree
					.getChildrenByProperty()) {
				for (final TreeNode<TSGNode> child : childProperties) {
					if (child.isLeaf()) {
						continue;
					}
					toVisit.push(child);
				}
			}
		}
		return nodes;
	}

	/**
	 * Return an identity map containing for all the nodes the immediate root in
	 * the TSG tree. Root is not included.
	 * 
	 * @param tree
	 * @return
	 */
	public static Map<TreeNode<TSGNode>, TreeNode<TSGNode>> getNodeToRootMap(
			final TreeNode<TSGNode> tree) {
		final Map<TreeNode<TSGNode>, TreeNode<TSGNode>> rootMap = Maps
				.newIdentityHashMap();

		// A queue containing the next node and the next node's root
		final ArrayDeque<CopyPair> toVisit = new ArrayDeque<CopyPair>();
		toVisit.push(new CopyPair(tree, tree));

		while (!toVisit.isEmpty()) {
			final CopyPair current = toVisit.pop();
			if (current.fromNode != current.toNode) {
				// This is not the root, then add
				rootMap.put(current.fromNode, current.toNode);
			}
			final TreeNode<TSGNode> nextRoot;
			if (current.fromNode.getData().isRoot) {
				nextRoot = current.fromNode;
			} else {
				nextRoot = current.toNode;
			}

			final List<List<TreeNode<TSGNode>>> children = current.fromNode
					.getChildrenByProperty();
			for (int i = 0; i < children.size(); i++) {
				final List<TreeNode<TSGNode>> childrenForProperty = children
						.get(i);
				for (final TreeNode<TSGNode> child : childrenForProperty) {
					toVisit.push(new CopyPair(child, nextRoot));
				}
			}
		}

		return rootMap;
	}

	/**
	 * Return the subtree from the current root node, to all leaves or
	 * children-root nodes.
	 * 
	 * @param topNode
	 * @return
	 */
	public static TreeNode<TSGNode> getSubTreeFromRoot(
			final TreeNode<TSGNode> topNode) {
		checkArgument(topNode.getData().isRoot);
		final TSGNode rootNode = new TSGNode(topNode.getData());
		final TreeNode<TSGNode> root = TreeNode.create(rootNode,
				topNode.nProperties());
		copyChildren(topNode, root, true);
		return root;
	}

	/**
	 * Wrap TreeNode<TSGNode> trees in TreeNode<Integer> trees.
	 * 
	 * @param treeRoot
	 * @return
	 */
	public static TreeNode<Integer> tsgTreeToInt(
			final TreeNode<TSGNode> treeRoot) {
		final TreeNode<Integer> root = TreeNode.create(
				treeRoot.getData().nodeKey, treeRoot.nProperties());
		copyChildren(root, treeRoot);
		return root;
	}

	public boolean isRoot;

	public final int nodeKey;

	public TSGNode(final int key) {
		nodeKey = key;
		isRoot = false;
	}

	public TSGNode(final TSGNode key) {
		nodeKey = key.nodeKey;
		isRoot = key.isRoot;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof TSGNode)) {
			return false;
		}
		final TSGNode other = (TSGNode) obj;
		return other.isRoot == isRoot && Objects.equal(nodeKey, other.nodeKey);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(nodeKey, isRoot);
	}

	@Override
	public String toString() {
		return "UnresolvedKey" + nodeKey + (isRoot ? " (Root)" : "");
	}
}
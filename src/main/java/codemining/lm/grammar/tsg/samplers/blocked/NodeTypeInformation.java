/**
 * 
 */
package codemining.lm.grammar.tsg.samplers.blocked;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.TSGNode;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * A helper class that can retrieve all the subtrees in a TSG tree with the same
 * type.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class NodeTypeInformation implements Serializable {

	/**
	 * A struct class for node types.
	 */
	public static class NodeType implements Serializable {

		private static final long serialVersionUID = -8065345305514413L;

		final int parentType;
		final int nodeType;

		final int[][] childrenTypes;

		public NodeType(final TreeNode<TSGNode> currentNode,
				final TreeNode<TSGNode> parent) {
			checkNotNull(currentNode);
			if (parent != null) {
				parentType = parent.getData().nodeKey;
			} else {
				parentType = -1;
			}
			nodeType = currentNode.getData().nodeKey;

			childrenTypes = new int[currentNode.nProperties()][];
			final List<List<TreeNode<TSGNode>>> children = currentNode
					.getChildrenByProperty();

			for (int i = 0; i < children.size(); i++) {
				final List<TreeNode<TSGNode>> childrenForProperty = children
						.get(i);
				childrenTypes[i] = new int[childrenForProperty.size()];
				for (int j = 0; j < childrenForProperty.size(); j++) {
					childrenTypes[i][j] = childrenForProperty.get(j).getData().nodeKey;
				}
			}
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final NodeType other = (NodeType) obj;
			if (nodeType != other.nodeType) {
				return false;
			}
			if (parentType != other.parentType) {
				return false;
			}
			if (!Arrays.deepEquals(childrenTypes, other.childrenTypes)) {
				return false;
			}
			return true;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(nodeType, parentType,
					Arrays.deepHashCode(childrenTypes));
		}

		@Override
		public String toString() {
			return "Type:" + nodeType + ", parentType:" + parentType
					+ ",children" + Arrays.deepToString(childrenTypes);
		}
	}

	private static final long serialVersionUID = -1917715699790189774L;

	static final Logger LOGGER = Logger.getLogger(NodeTypeInformation.class
			.getName());

	/**
	 * Get the nodes in the joint rule from nodeRoot, excluding base Node
	 * 
	 * @param baseNode
	 * @param nodeRoot
	 * @return
	 */
	public static Set<TreeNode<TSGNode>> getNodesInSelf(
			final TreeNode<TSGNode> baseNode, final TreeNode<TSGNode> nodeRoot) {
		checkArgument(nodeRoot != baseNode);
		checkArgument(!baseNode.getData().isRoot);
		final Set<TreeNode<TSGNode>> nodesInSelf = Sets.newIdentityHashSet();
		final ArrayDeque<TreeNode<TSGNode>> toVisit = new ArrayDeque<TreeNode<TSGNode>>();

		toVisit.push(nodeRoot);
		nodesInSelf.add(nodeRoot);
		while (!toVisit.isEmpty()) {
			final TreeNode<TSGNode> current = toVisit.pop();

			for (final List<TreeNode<TSGNode>> childrenForProperty : current
					.getChildrenByProperty()) {
				for (final TreeNode<TSGNode> child : childrenForProperty) {
					if (!child.getData().isRoot) {
						toVisit.push(child);
					}
					if (child != baseNode) {
						nodesInSelf.add(child);
					}
				}
			}

		}

		return nodesInSelf;
	}

	/**
	 * A map containing the parents of all nodes.
	 */
	private final Map<TreeNode<TSGNode>, TreeNode<TSGNode>> parentMap = Maps
			.newIdentityHashMap();

	private final Map<NodeType, Set<TreeNode<TSGNode>>> nodeTypes = Maps
			.newHashMap();

	/**
	 * Add a single node to it's type.
	 * 
	 * @param current
	 * @param type
	 */
	private void addNodeToTypeMap(final TreeNode<TSGNode> current,
			final NodeTypeInformation.NodeType type) {
		Set<TreeNode<TSGNode>> nodesForType = nodeTypes.get(type);
		if (nodesForType == null) {
			nodesForType = Sets.newIdentityHashSet();
			nodeTypes.put(type, nodesForType);
		}
		nodesForType.add(current);
	}

	/**
	 * Returns true if the roots are of the same type (i.e. have the same number
	 * of children, properties and type) and the child1,child2 are at the same
	 * position as children.
	 */
	private boolean areIsomorphicNodes(final TreeNode<TSGNode> child1,
			final TreeNode<TSGNode> root1, final TreeNode<TSGNode> child2,
			final TreeNode<TSGNode> root2) {
		if (!root1.getData().equals(root2.getData())) {
			return false; // Root nodes do not match.
		}
		final List<List<TreeNode<TSGNode>>> children1 = root1
				.getChildrenByProperty();
		final List<List<TreeNode<TSGNode>>> children2 = root2
				.getChildrenByProperty();

		final int childrenSize = children1.size();
		if (childrenSize != children2.size()) {
			return false; // Sizes do not match
		}

		// Speed-up things using
		boolean found = false;
		for (int i = 0; i < childrenSize; i++) {
			final List<TreeNode<TSGNode>> childrenForProperty1 = children1
					.get(i);
			final List<TreeNode<TSGNode>> childrenForProperty2 = children2
					.get(i);

			final int childrenForPropertySize = childrenForProperty1.size();
			if (childrenForPropertySize != childrenForProperty2.size()) {
				return false; // Sizes do not match
			}

			if (!found) {
				for (int j = 0; j < childrenForPropertySize; j++) {
					if (child1 == childrenForProperty1.get(j)) {
						if (child2 != childrenForProperty2.get(j)) {
							// The children are not coming from the same path
							return false;
						} else {
							found = true;
							break;
						}
					}
				}
			}
		}
		return found;
	}

	/**
	 * Return a set of candidate nodes for the type of this node, including this
	 * node itself.
	 */
	private Collection<TreeNode<TSGNode>> getCandidateSameTypeNodes(
			final TreeNode<TSGNode> node) {
		checkNotNull(node);
		final NodeType type = new NodeType(node, parentMap.get(node));
		final Set<TreeNode<TSGNode>> sameParentChildrenNodes = nodeTypes
				.get(type);
		checkArgument(sameParentChildrenNodes.size() >= 1,
				"The node itself should be included here");
		return Collections.unmodifiableCollection(sameParentChildrenNodes);
	}

	/**
	 * Return the parent of a node.
	 * 
	 * @param node
	 * @return
	 */
	public TreeNode<TSGNode> getParentOf(final TreeNode<TSGNode> node) {
		return parentMap.get(node);
	}

	/**
	 * Get the root of this node in the corpus. If this is the root of a tree,
	 * null will be returned.
	 * 
	 * @param node
	 * @return
	 */
	public TreeNode<TSGNode> getRootForNode(final TreeNode<TSGNode> node) {
		TreeNode<TSGNode> nextNode = parentMap.get(node);
		while (nextNode != null && !nextNode.getData().isRoot) {
			nextNode = parentMap.get(nextNode);
		}
		return nextNode;
	}

	/**
	 * Return the references to the nodes that are of exactly the same type.
	 * 
	 * @param baseNode
	 * @return
	 */
	public Collection<TreeNode<TSGNode>> getSameTypeNodes(
			final TreeNode<TSGNode> baseNode) {
		final TreeNode<TSGNode> nodeRoot = getRootForNode(baseNode);
		final Collection<TreeNode<TSGNode>> candidateNodes = getCandidateSameTypeNodes(baseNode);

		// Get initial state, to be restored at the end and set as roots
		final Map<TreeNode<TSGNode>, Boolean> initialState = Maps
				.newIdentityHashMap();
		for (final TreeNode<TSGNode> candidateNode : candidateNodes) {
			initialState.put(candidateNode, candidateNode.getData().isRoot);
		}

		baseNode.getData().isRoot = false;
		final Set<TreeNode<TSGNode>> illegalNodes = getNodesInSelf(baseNode,
				nodeRoot);

		// First check that the candidates match the upper path
		final Set<TreeNode<TSGNode>> sameTypeNodes = Sets.newIdentityHashSet();
		for (final TreeNode<TSGNode> candidateNode : candidateNodes) {
			if (!illegalNodes.contains(candidateNode)
					&& haveSamePathToUpperRoot(baseNode, nodeRoot,
							candidateNode)) {
				sameTypeNodes.add(candidateNode);
				candidateNode.getData().isRoot = false;
				illegalNodes.addAll(getNodesInSelf(candidateNode,
						getRootForNode(candidateNode)));
			}
		}

		// Check that the candidates match the joined tree
		final List<TreeNode<TSGNode>> toBeRemoved = Lists.newArrayList();

		for (final TreeNode<TSGNode> candidateTree : sameTypeNodes) {
			final TreeNode<TSGNode> candidateTreeRoot = getRootForNode(candidateTree);
			if (!TSGNode.treesMatchToRoot(nodeRoot, candidateTreeRoot)) {
				toBeRemoved.add(candidateTree);
			}
		}
		for (final TreeNode<TSGNode> node : toBeRemoved) {
			sameTypeNodes.remove(node);
		}

		// Restore root states
		for (final TreeNode<TSGNode> candidateNode : candidateNodes) {
			candidateNode.getData().isRoot = initialState.get(candidateNode);
		}

		checkArgument(sameTypeNodes.contains(baseNode));
		return sameTypeNodes;
	}

	/**
	 * Returns true iff the starting from fromNode1 to reach rootNode1, the
	 * exactly same path is followed (type-wise) and root-wise. This is
	 * necessary, since the upper trees may match type-wise but not the same
	 * path is followed.
	 */
	private boolean haveSamePathToUpperRoot(final TreeNode<TSGNode> fromNode1,
			final TreeNode<TSGNode> rootNode1, final TreeNode<TSGNode> fromNode2) {
		checkNotNull(rootNode1);
		// Start following both nodes to rootNode1 checking that they are
		// isomorphic
		TreeNode<TSGNode> from1 = fromNode1;
		TreeNode<TSGNode> from2 = fromNode2;
		TreeNode<TSGNode> parentNode1 = parentMap.get(from1);
		TreeNode<TSGNode> parentNode2 = parentMap.get(from2);
		while (parentNode1 != rootNode1) {
			if (areIsomorphicNodes(from1, parentNode1, from2, parentNode2)) {
				from1 = parentNode1;
				from2 = parentNode2;
				parentNode1 = parentMap.get(parentNode1);
				parentNode2 = parentMap.get(parentNode2);
			} else {
				return false;
			}
		}
		return areIsomorphicNodes(from1, parentNode1, from2, parentNode2);
	}

	/**
	 * Adds nodes to parent map and to type map. The root will return null.
	 * 
	 * @param root
	 */
	public void updateCorpusStructures(final TreeNode<TSGNode> root) {
		final ArrayDeque<TreeNode<TSGNode>> stack = new ArrayDeque<TreeNode<TSGNode>>();
		stack.add(root);

		while (!stack.isEmpty()) {
			final TreeNode<TSGNode> current = stack.pop();
			final NodeTypeInformation.NodeType type = new NodeTypeInformation.NodeType(
					current, parentMap.get(current));
			addNodeToTypeMap(current, type);

			final List<List<TreeNode<TSGNode>>> children = current
					.getChildrenByProperty();
			for (int i = 0; i < children.size(); i++) {
				for (final TreeNode<TSGNode> child : children.get(i)) {
					// add to parent map
					parentMap.put(child, current);
					// push next
					stack.push(child);
				}
			}
		}
	}

}

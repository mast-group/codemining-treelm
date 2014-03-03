/**
 * 
 */
package codemining.lm.grammar.tree;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;

import codemining.util.data.Pair;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * A generic tree node
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
@DefaultSerializer(JavaSerializer.class)
public final class TreeNode<T extends Serializable> implements Serializable {
	/**
	 * Struct class for node data pairs
	 * 
	 * @param <T>
	 */
	public static final class NodeDataPair<T extends Serializable> {
		public final T fromNode;

		public final T toNode;

		public NodeDataPair(final T from, final T to) {
			fromNode = from;
			toNode = to;
		}
	}

	/**
	 * A struct class containing from and to pair of nodes to copy.
	 * 
	 */
	public static final class NodePair<T extends Serializable> {
		public final TreeNode<T> fromNode;

		public final TreeNode<T> toNode;

		public NodePair(final TreeNode<T> from, final TreeNode<T> to) {
			fromNode = from;
			toNode = to;
		}
	}

	/**
	 * A struct computing and containing the parent nodes of a given target node
	 * in a tree. The lists contain the nodes and the "directions" to reach the
	 * target node. The first node in the list is the parent of the target of
	 * the node, while the last is the root.
	 * 
	 * The implementation includes a slow, recursive solution. But it is the
	 * easiest for understanding.
	 */
	public static class NodeParents<T extends Serializable> {
		public final TreeNode<T> targetNode;

		public final List<TreeNode<T>> throughNodes = Lists.newArrayList();

		public final List<Integer> nextProperty = Lists.newArrayList();

		public final List<Integer> nextChildIndex = Lists.newArrayList();

		public NodeParents(final TreeNode<T> root, final TreeNode<T> targetNode) {
			this.targetNode = targetNode;
			final boolean pathFound = reachTarget(root);
			checkArgument(pathFound);
		}

		private boolean reachTarget(final TreeNode<T> currentNode) {
			if (currentNode == targetNode) {
				return true;
			}

			final List<List<TreeNode<T>>> children = currentNode.childrenProperties;

			for (int propertyId = 0; propertyId < children.size(); propertyId++) {
				final List<TreeNode<T>> childrenForProperty = children
						.get(propertyId);
				for (int i = 0; i < childrenForProperty.size(); i++) {
					final TreeNode<T> currentChild = childrenForProperty.get(i);
					final boolean isInPath = reachTarget(currentChild);
					if (isInPath) {
						throughNodes.add(currentNode);
						nextProperty.add(propertyId);
						nextChildIndex.add(i);
						return true;
					}
				}
			}

			return false;
		}

	}

	/**
	 * A struct for passing a tree along with references to some nodes.
	 * 
	 * @param <T>
	 */
	public static final class NodeWithRef<T extends Serializable> {
		public static <T extends Serializable> NodeWithRef<T> createNodeCompare(
				final TreeNode<T> node, final Set<TreeNode<T>> references,
				final TreeNode<T> currentReference) {
			final NodeWithRef<T> cmp = new NodeWithRef<T>();
			cmp.node = node;
			cmp.references = references;
			cmp.currentReference = currentReference;
			return cmp;
		}

		public TreeNode<T> node;
		public TreeNode<T> currentReference;
		public Set<TreeNode<T>> references;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -3543181013512815033L;

	/**
	 * A static constant used for String conversion
	 */
	public static final String SUB_NODE_STRING_PREFIX = "-";

	/**
	 * Copy the children (and all (grand+)children) to the given toNode. This
	 * will copy only the structure. The data will be the same.
	 * 
	 * @param fromNode
	 * @param toNode
	 */
	private static <T extends Serializable> void copyChildren(
			final TreeNode<T> fromNode, final TreeNode<T> toNode) {
		final ArrayDeque<NodePair<T>> stack = new ArrayDeque<NodePair<T>>();

		stack.push(new NodePair<T>(fromNode, toNode));

		while (!stack.isEmpty()) {
			final NodePair<T> pair = stack.pop();
			final TreeNode<T> currentFrom = pair.fromNode;
			final TreeNode<T> currentTo = pair.toNode;

			final List<List<TreeNode<T>>> children = currentFrom
					.getChildrenByProperty();

			for (int i = 0; i < children.size(); i++) {
				for (final TreeNode<T> fromChild : children.get(i)) {
					final TreeNode<T> toChild = TreeNode.create(
							fromChild.getData(), fromChild.nProperties());
					currentTo.addChildNode(toChild, i);

					stack.push(new NodePair<T>(fromChild, toChild));
				}
			}
		}
	}

	/**
	 * Copy the children (and all (grand+)children) to the given toNode. This
	 * will copy only the structure. The data will be the same.
	 * 
	 * @param fromNode
	 * @param toNode
	 * @param stopOnRoots
	 */
	private static <T extends Serializable> NodeWithRef<T> copyChildren(
			final TreeNode<T> fromNode, final TreeNode<T> toNode,
			final Set<TreeNode<T>> references,
			final TreeNode<T> currentReference) {
		final ArrayDeque<NodePair<T>> stack = new ArrayDeque<NodePair<T>>();
		final Set<TreeNode<T>> referencesCopy = Sets.newHashSet();
		if (references.contains(fromNode)) {
			referencesCopy.add(toNode);
		}

		TreeNode<T> currentReferenceCopy = null;
		if (currentReference == fromNode) {
			currentReferenceCopy = toNode;
		}

		stack.push(new NodePair<T>(fromNode, toNode));

		while (!stack.isEmpty()) {
			final NodePair<T> pair = stack.pop();
			final TreeNode<T> currentFrom = pair.fromNode;
			final TreeNode<T> currentTo = pair.toNode;

			final List<List<TreeNode<T>>> children = currentFrom
					.getChildrenByProperty();

			for (int i = 0; i < children.size(); i++) {
				for (final TreeNode<T> fromChild : children.get(i)) {
					final TreeNode<T> toChild = TreeNode.create(
							fromChild.getData(), fromChild.nProperties());
					currentTo.addChildNode(toChild, i);

					stack.push(new NodePair<T>(fromChild, toChild));
					if (references.contains(fromChild)) {
						referencesCopy.add(toChild);
					}
					if (currentReference == fromChild) {
						currentReferenceCopy = toChild;
					}
				}
			}
		}

		return NodeWithRef.createNodeCompare(toNode, referencesCopy,
				currentReferenceCopy);
	}

	/**
	 * Static utility to create TreeNode.
	 * 
	 * @param data
	 * @param size
	 * @return
	 */
	public static final <T extends Serializable> TreeNode<T> create(
			final T data, final int size) {
		return new TreeNode<T>(data, size);
	}

	/**
	 * Static utility to create TreeNode from another TreeNode.
	 * 
	 * @return
	 */
	public static final <T extends Serializable> TreeNode<T> create(
			final TreeNode<T> tree) {
		return new TreeNode<T>(tree.getData(), tree.nProperties());
	}

	/**
	 * The children of this node. This is a list of lists. One list for each
	 * property.
	 */
	private final List<List<TreeNode<T>>> childrenProperties;

	/**
	 * The details of the tree node.
	 */
	private final T nodeData;

	/**
	 * Construct a Node give its data.
	 * 
	 * @param name
	 *            the name/data of the node
	 */
	private TreeNode(final T name, final int nProperties) {
		nodeData = name;
		childrenProperties = Lists.newArrayListWithCapacity(nProperties);
		for (int i = 0; i < nProperties; i++) {
			final List<TreeNode<T>> childrenElements = Lists.newArrayList();
			childrenProperties.add(childrenElements);
		}
	}

	/**
	 * Create an immutable node with this data.
	 * 
	 * @param name
	 * @param children
	 */
	private TreeNode(final T name, final List<List<TreeNode<T>>> children) {
		nodeData = name;
		this.childrenProperties = ImmutableList.copyOf(children);
	}

	/**
	 * Add a child to this node.
	 * 
	 * @param child
	 */
	public synchronized void addChildNode(final TreeNode<T> child,
			final int propertyIndex) {
		final List<TreeNode<T>> childrenPlaceholder = childrenProperties
				.get(checkElementIndex(propertyIndex, childrenProperties.size()));
		childrenPlaceholder.add(child);
	}

	/**
	 * Create a deep copy of the TreeNode structure. Data of each node, still
	 * refers to the same element.
	 * 
	 * @return
	 */
	public TreeNode<T> deepCopy() {
		final TreeNode<T> toChild = TreeNode.create(nodeData, nProperties());
		TreeNode.copyChildren(this, toChild);
		return toChild;
	}

	/**
	 * Return a deep copy of this tree node and a reference to a child in the
	 * copied tree that matches the node in this tree.
	 * 
	 * @param references
	 * @return a pair of nodes. The first one is the copied tree, the second is
	 *         the reference.
	 */
	public NodeWithRef<T> deepCopyWithReferences(
			final Set<TreeNode<T>> references,
			final TreeNode<T> currentReference) {
		final TreeNode<T> toChild = TreeNode.create(nodeData, nProperties());
		return TreeNode.copyChildren(this, toChild, references,
				currentReference);

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
		final TreeNode<T> other = (TreeNode<T>) obj;
		// Check equalities here, for speedup
		if (!nodeData.equals(other.nodeData)) {
			return false;
		}

		final ArrayDeque<NodePair<T>> stack = new ArrayDeque<NodePair<T>>();
		stack.push(new NodePair<T>(this, other));

		while (!stack.isEmpty()) {
			final NodePair<T> pair = stack.pop();
			final TreeNode<T> currentThis = pair.fromNode;
			final TreeNode<T> currentOther = pair.toNode;

			final List<List<TreeNode<T>>> thisChildren = currentThis
					.getChildrenByProperty();
			final List<List<TreeNode<T>>> otherChildren = currentOther
					.getChildrenByProperty();

			final int thisChildrenSize = thisChildren.size();
			if (thisChildrenSize != otherChildren.size()) {
				return false;
			}

			for (int i = 0; i < thisChildrenSize; i++) {
				final List<TreeNode<T>> thisChildrenByProperty = thisChildren
						.get(i);
				final List<TreeNode<T>> otherChildrenByProperty = otherChildren
						.get(i);

				final int thisChildByPropertySize = thisChildrenByProperty
						.size();
				if (thisChildByPropertySize != otherChildrenByProperty.size()) {
					return false;
				}

				for (int j = 0; j < thisChildByPropertySize; j++) {
					final TreeNode<T> thisChild = thisChildrenByProperty.get(j);
					final TreeNode<T> otherChild = otherChildrenByProperty
							.get(j);

					if (!otherChild.getData().equals(thisChild.getData())) {
						return false;
					}

					stack.push(new NodePair<T>(thisChild, otherChild));
				}
			}
		}
		return true;
	}

	/**
	 * Get the i-th child
	 * 
	 * @param i
	 * @return
	 */
	public TreeNode<T> getChild(final int i, final int propertyId) {
		return childrenProperties.get(propertyId).get(i);
	}

	/**
	 * Return all the children of this node.
	 * 
	 * @return
	 */
	public List<List<TreeNode<T>>> getChildrenByProperty() {
		return childrenProperties;
	}

	/**
	 * Return the node data.
	 * 
	 * @return
	 */
	public T getData() {
		return nodeData;
	}

	/**
	 * Return the parents of this node from a root node.
	 * 
	 * @param fromRoot
	 * @return
	 */
	public NodeParents<T> getNodeParents(final TreeNode<T> fromRoot) {
		return new NodeParents<T>(fromRoot, this);
	}

	/**
	 * Compute the identity set of the nodes that overlap with the other tree.
	 * 
	 * @param tree1
	 * @param tree2
	 * @return
	 */
	public Set<TreeNode<T>> getOverlappingNodesWith(final TreeNode<T> other) {
		final ArrayDeque<Pair<TreeNode<T>, TreeNode<T>>> stack = new ArrayDeque<Pair<TreeNode<T>, TreeNode<T>>>();
		stack.push(Pair.create(this, other));

		final Set<TreeNode<T>> overlapping = Sets.newIdentityHashSet();
		while (!stack.isEmpty()) {
			final Pair<TreeNode<T>, TreeNode<T>> current = stack.pop();
			final TreeNode<T> tree1Node = current.first;
			final TreeNode<T> tree2Node = current.second;
			if (!tree1Node.getData().equals(tree2Node.getData())) {
				continue;
			}
			overlapping.add(tree1Node);

			final List<List<TreeNode<T>>> tree1Children = tree1Node
					.getChildrenByProperty();
			final List<List<TreeNode<T>>> tree2Children = tree2Node
					.getChildrenByProperty();

			checkArgument(tree1Children.size() == tree2Children.size());

			for (int i = 0, size = tree1Children.size(); i < size; i++) {
				final List<TreeNode<T>> tree1ChildrenForProperty = tree1Children
						.get(i);
				final List<TreeNode<T>> tree2ChildrenForProperty = tree2Children
						.get(i);

				final int nChildren = Math.min(tree1ChildrenForProperty.size(),
						tree2ChildrenForProperty.size());
				for (int j = 0; j < nChildren; j++) {
					stack.push(Pair.create(tree1ChildrenForProperty.get(j),
							tree2ChildrenForProperty.get(j)));
				}
			}
		}

		return overlapping;
	}

	/**
	 * Return the tree size of this tree.
	 * 
	 * @return
	 */
	public int getTreeSize() {
		final ArrayDeque<TreeNode<T>> toLook = new ArrayDeque<TreeNode<T>>();
		int size = 1;
		toLook.push(this);
		while (!toLook.isEmpty()) {
			final TreeNode<T> currentNode = toLook.pop();

			for (final List<TreeNode<T>> childProperties : currentNode
					.getChildrenByProperty()) {
				size += childProperties.size();
				for (final TreeNode<T> child : childProperties) {
					toLook.push(child);
				}
			}
		}
		return size;
	}

	@Override
	public int hashCode() {
		if (childrenProperties.size() > 0) {
			return Objects.hashCode(nodeData, childrenProperties.get(0)
					.hashCode());
		} else {
			return Objects.hashCode(nodeData);
		}
	}

	public boolean isLeaf() {
		for (final List<TreeNode<T>> childProperty : childrenProperties) {
			if (!childProperty.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public boolean isPartialSubtreeOf(final TreeNode<T> other) {
		return isPartialSubtreeOf(other, new Predicate<NodeDataPair<T>>() {
			@Override
			public boolean apply(final NodeDataPair<T> arg) {
				return arg.fromNode.equals(arg.toNode);
			}
		});
	}

	/**
	 * Returns true if this node is a subtree of the other node. This means that
	 * this tree can be fully found in the other tree.
	 * 
	 * @param other
	 * @param equalityComparator
	 * @return
	 */
	public boolean isPartialSubtreeOf(final TreeNode<T> other,
			final Predicate<NodeDataPair<T>> equalityComparator) {
		final ArrayDeque<NodePair<T>> stack = new ArrayDeque<NodePair<T>>();

		stack.push(new NodePair<T>(this, other));
		while (!stack.isEmpty()) {
			final NodePair<T> currentNodes = stack.pop();
			final TreeNode<T> thisNode = currentNodes.fromNode;
			final TreeNode<T> otherNode = currentNodes.toNode;

			if (!equalityComparator.apply(new NodeDataPair<T>(
					thisNode.nodeData, otherNode.getData()))) {
				return false;
			} else if (thisNode.nProperties() != otherNode.nProperties()) {
				return false;
			}

			final List<List<TreeNode<T>>> thisChildren = thisNode.childrenProperties;
			final List<List<TreeNode<T>>> otherChildren = otherNode.childrenProperties;
			for (int propertyId = 0; propertyId < thisChildren.size(); propertyId++) {
				final List<TreeNode<T>> thisProperty = thisChildren
						.get(propertyId);
				final List<TreeNode<T>> otherProperty = otherChildren
						.get(propertyId);
				if (thisProperty.size() > otherProperty.size()) {
					return false;
				}
				for (int i = 0; i < thisProperty.size(); i++) {
					stack.push(new NodePair<T>(thisProperty.get(i),
							otherProperty.get(i)));
				}
			}
		}

		return true;
	}

	public boolean isPartialSupertreeOf(final TreeNode<T> other) {
		return isPartialSupertreeOf(other, new Predicate<NodeDataPair<T>>() {
			@Override
			public boolean apply(final NodeDataPair<T> arg) {
				return arg.fromNode.equals(arg.toNode);
			}
		});
	}

	/**
	 * Returns true if this node is a partial supertree of the other node. This
	 * means that this tree can be partially found in the other tree, but this
	 * tree may have more children.
	 * 
	 * @param other
	 * @param equalityComparator
	 * @return
	 */
	public boolean isPartialSupertreeOf(final TreeNode<T> other,
			final Predicate<NodeDataPair<T>> equalityComparator) {
		final ArrayDeque<NodePair<T>> stack = new ArrayDeque<NodePair<T>>();

		stack.push(new NodePair<T>(this, other));
		while (!stack.isEmpty()) {
			final NodePair<T> currentNodes = stack.pop();
			final TreeNode<T> thisNode = currentNodes.fromNode;
			final TreeNode<T> otherNode = currentNodes.toNode;

			if (!equalityComparator.apply(new NodeDataPair<T>(
					thisNode.nodeData, otherNode.getData()))) {
				return false;
			} else if (thisNode.nProperties() != otherNode.nProperties()) {
				return false;
			}

			if (thisNode.isLeaf()) {
				continue;
			} else if (otherNode.isLeaf() && !thisNode.isLeaf()) {
				return false;
			}

			final List<List<TreeNode<T>>> thisChildren = thisNode.childrenProperties;
			final List<List<TreeNode<T>>> otherChildren = otherNode.childrenProperties;

			for (int propertyId = 0; propertyId < thisChildren.size(); propertyId++) {
				final List<TreeNode<T>> thisProperty = thisChildren
						.get(propertyId);
				final List<TreeNode<T>> otherProperty = otherChildren
						.get(propertyId);

				if (thisProperty.size() < otherProperty.size()) {
					return false;
				}
				for (int i = 0; i < otherProperty.size(); i++) {
					stack.push(new NodePair<T>(thisProperty.get(i),
							otherProperty.get(i)));
				}
			}
		}

		return true;
	}

	public int nProperties() {
		return childrenProperties.size();
	}

	/**
	 * Returns true if this is a partial match. Avoid using this function
	 * frequently since it instantiates the predicate on the fly.
	 * 
	 * @param other
	 * @return
	 */
	public boolean partialMatch(final TreeNode<T> other,
			final boolean requireAllChildren) {
		return partialMatch(other, new Predicate<NodeDataPair<T>>() {
			@Override
			public boolean apply(final NodeDataPair<T> arg) {
				return arg.fromNode.equals(arg.toNode);
			}
		}, requireAllChildren);
	}

	/**
	 * returns true if it partially matches the other tree. A partial match is
	 * defined when this node's children are a subset of the other's children
	 * and have matching data. Node data equality is defined by the given
	 * predicate.
	 * 
	 * @param other
	 * @param equalityComparator
	 * @param requireAllChildren
	 *            require to match all children (if a node has one, then it
	 *            should match all of them)
	 * @return
	 */
	public boolean partialMatch(final TreeNode<T> other,
			final Predicate<NodeDataPair<T>> equalityComparator,
			final boolean requireAllChildren) {
		if (!equalityComparator.apply(new NodeDataPair<T>(nodeData,
				other.nodeData))) {
			return false;
		}

		final ArrayDeque<NodePair<T>> stack = new ArrayDeque<NodePair<T>>();

		stack.push(new NodePair<T>(this, other));
		while (!stack.isEmpty()) {
			final NodePair<T> current = stack.pop();
			final TreeNode<T> thisNode = current.fromNode;
			final TreeNode<T> otherNode = current.toNode;

			if (!equalityComparator.apply(new NodeDataPair<T>(
					thisNode.nodeData, otherNode.nodeData))) {
				return false;
			}

			if (thisNode.nProperties() != otherNode.nProperties()) {
				return false;
			}

			boolean hasChildren = false;
			boolean sizesSame = true;
			for (int i = 0, n = thisNode.childrenProperties.size(); i < n; i++) {
				final List<TreeNode<T>> children = thisNode.childrenProperties
						.get(i);
				final List<TreeNode<T>> otherChildren = otherNode.childrenProperties
						.get(i);

				if (children.size() != otherChildren.size()) {
					sizesSame = false;
				}

				if (children.size() > 0) {
					hasChildren = true;
				}

				if (children.size() > otherChildren.size()
						&& !requireAllChildren) {
					return false;
				} else if (requireAllChildren && hasChildren && !sizesSame) {
					return false;
				}

				for (int j = 0; j < children.size(); j++) {
					stack.push(new NodePair<T>(children.get(j), otherChildren
							.get(j)));
				}

			}
		}
		return true;
	}

	/**
	 * Return an immutable copy of this the subtree rooted at this node.
	 * 
	 * @return
	 */
	public TreeNode<T> toImmutable() {
		final List<List<TreeNode<T>>> immutableProperties = Lists
				.newArrayList();
		for (int i = 0; i < childrenProperties.size(); i++) {
			final List<TreeNode<T>> immutableChildren = Lists.newArrayList();
			for (final TreeNode<T> child : childrenProperties.get(i)) {
				immutableChildren.add(child.toImmutable());
			}
			immutableProperties.add(ImmutableList.copyOf(immutableChildren));
		}
		return new TreeNode<T>(nodeData,
				ImmutableList.copyOf(immutableProperties));
	}

	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer();
		treePrinterHelper(sb, this, "", new Function<TreeNode<T>, String>() {

			@Override
			public String apply(final TreeNode<T> node) {
				return node.getData().toString();
			}
		});
		return sb.toString();
	}

	/**
	 * Use a functional to convert the data to a string.
	 * 
	 * @param toStringConverter
	 * @return
	 */
	public String toString(final Function<TreeNode<T>, String> toStringConverter) {
		final StringBuffer sb = new StringBuffer();
		treePrinterHelper(sb, this, "", toStringConverter);
		return sb.toString();
	}

	/**
	 * Helper recursive function for printing the tree.
	 * 
	 * @param buffer
	 * @param currentNode
	 * @param prefix
	 */
	public void treePrinterHelper(final StringBuffer buffer,
			final TreeNode<T> currentNode, final String prefix,
			final Function<TreeNode<T>, String> dataToString) {
		buffer.append(prefix);
		if (currentNode != null) {
			buffer.append(dataToString.apply(currentNode));
			buffer.append('\n');
			for (int i = 0; i < currentNode.childrenProperties.size(); i++) {
				for (final TreeNode<T> child : currentNode.childrenProperties
						.get(i)) {
					treePrinterHelper(buffer, child, prefix
							+ SUB_NODE_STRING_PREFIX + "(" + i + ")",
							dataToString);
				}
			}
		} else {
			buffer.append("NULL\n");
		}
	}
}

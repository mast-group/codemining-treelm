/**
 * 
 */
package codemining.lm.grammar.tsg;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import codemining.lm.grammar.tree.ASTNodeSymbol;
import codemining.lm.grammar.tree.TreeNode;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.SortedMultiset;

/**
 * A tree substitution grammar that is able to gracefully handle nodes that have
 * more than one child. Forwards implementation to a base grammar.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class SequentialTSGrammar extends JavaFormattedTSGrammar {

	private static final long serialVersionUID = 454485199220281131L;

	final JavaFormattedTSGrammar baseGrammar;

	/**
	 * @param format
	 */
	public SequentialTSGrammar(final JavaFormattedTSGrammar base) {
		super(null);
		baseGrammar = base;
	}

	@Override
	public void addTree(final TreeNode<TSGNode> root) {
		for (final TreeNode<TSGNode> tree : convertTreeNode(root, true)) {
			baseGrammar.addTree(tree);
		}
	}

	@Override
	public SortedMultiset<Integer> computeGrammarTreeSizeStats() {
		return baseGrammar.computeGrammarTreeSizeStats();
	}

	/**
	 * Convert a tree node to a set of nodes. Assumes that the given tree has
	 * been parsed by the TSGNode.getSubTreeFromRoot and thus no further
	 * splitting is needed. Only child nodes that contain more than 2 nodes will
	 * be changed.
	 * 
	 * @param tree
	 * @return
	 */
	public Multiset<TreeNode<TSGNode>> convertTreeNode(
			final TreeNode<TSGNode> tree, final boolean createMultinodes) {
		final Multiset<TreeNode<TSGNode>> convertedTrees = HashMultiset
				.create();

		final TreeNode<TSGNode> toTree = TreeNode.create(tree.getData(),
				tree.nProperties());
		convertedTrees.add(toTree);

		final ArrayDeque<TreeNode<TSGNode>> toStack = new ArrayDeque<TreeNode<TSGNode>>();
		final ArrayDeque<TreeNode<TSGNode>> fromStack = new ArrayDeque<TreeNode<TSGNode>>();

		toStack.push(toTree);
		fromStack.push(tree);

		// Start walking the tree.
		while (!toStack.isEmpty()) {
			final TreeNode<TSGNode> currentTo = toStack.pop();
			final TreeNode<TSGNode> currentFrom = fromStack.pop();

			final List<List<TreeNode<TSGNode>>> children = currentFrom
					.getChildrenByProperty();

			for (int i = 0; i < children.size(); i++) {
				final List<TreeNode<TSGNode>> childrenForProperty = children
						.get(i);
				if (childrenForProperty.size() <= 2) {
					for (final TreeNode<TSGNode> fromChild : childrenForProperty) {
						final TreeNode<TSGNode> toChild = TreeNode.create(
								fromChild.getData(), fromChild.nProperties());
						currentTo.addChildNode(toChild, i);
						toStack.push(toChild);
						fromStack.push(fromChild);
					}
				} else {
					// OK. This is complicated, but cannot think of anything
					// better. As we will be seeing root nodes we will be
					// keeping track of them. When we see non-root nodes, we
					// will add the multinodes too.
					boolean multinodeNeedsToBeAdded = false;
					int startPos = -1;
					int endPos = -1;

					for (int j = 0; j < childrenForProperty.size(); j++) {
						final TreeNode<TSGNode> fromChild = childrenForProperty
								.get(j);
						if (!fromChild.getData().isRoot) {
							processMultinodes(convertedTrees, currentTo, i,
									childrenForProperty,
									multinodeNeedsToBeAdded, startPos, endPos,
									createMultinodes);
							multinodeNeedsToBeAdded = false;

							final TreeNode<TSGNode> toChild = TreeNode.create(
									fromChild.getData(),
									fromChild.nProperties());
							currentTo.addChildNode(toChild, i);
							toStack.push(toChild);
							fromStack.push(fromChild);
						} else {
							if (!multinodeNeedsToBeAdded) {
								multinodeNeedsToBeAdded = true;
								startPos = j - 1;
							}
							endPos = j + 1;
						}
					}
					// And don't forget the last few nodes...
					processMultinodes(convertedTrees, currentTo, i,
							childrenForProperty, multinodeNeedsToBeAdded,
							startPos, endPos, createMultinodes);

				}
			}
		}

		return convertedTrees;
	}

	@Override
	public int countTreeOccurences(final TreeNode<TSGNode> root) {
		// Assumes that the whole tree is in the grammar (i.e. no multinode
		// conversion). TODO: Right?
		return baseGrammar.countTreeOccurences(root);
	}

	@Override
	public int countTreesWithRoot(final TSGNode root) {
		return baseGrammar.countTreesWithRoot(root);
	}

	/**
	 * @param childrenForProperty
	 * @param rootNode
	 * @param k
	 * @return
	 */
	public TreeNode<TSGNode> createMultinodeTree(
			final List<TreeNode<TSGNode>> childrenForProperty,
			final TSGNode rootNode, final int k) {
		final TreeNode<TSGNode> fromMultinode = TreeNode.create(rootNode, 2);
		final TreeNode<TSGNode> toMultinode = TreeNode.create(rootNode, 2);

		final TreeNode<TSGNode> intermediateNode = childrenForProperty.get(k);
		final TreeNode<TSGNode> intermediateNodeCopy = TreeNode.create(
				intermediateNode.getData(), intermediateNode.nProperties());

		fromMultinode.addChildNode(intermediateNodeCopy, 0);
		fromMultinode.addChildNode(toMultinode, 1);
		return fromMultinode;
	}

	@Override
	public TreeNode<TSGNode> generateRandom(final TreeNode<TSGNode> root) {
		// TODO after something random has been generated, start removing the
		// multinodes...
		return baseGrammar.generateRandom(root);
	}

	@Override
	public Map<TSGNode, ? extends Multiset<TreeNode<TSGNode>>> getInternalGrammar() {
		return baseGrammar.getInternalGrammar();
	}

	/**
	 * Return a multinode for a given range.
	 * 
	 * @param childrenForProperty
	 * @param startPos
	 * @param endPos
	 * @return
	 */
	public int getMultinodeSymbolIdFor(
			final List<TreeNode<TSGNode>> childrenForProperty,
			final int startPos, final int endPos) {
		final ASTNodeSymbol multinodeSymbol = new ASTNodeSymbol(
				ASTNodeSymbol.MULTI_NODE);
		multinodeSymbol.addChildProperty("NODE");
		multinodeSymbol.addChildProperty("NEXT");
		if (startPos >= 0) {
			multinodeSymbol.addAnnotation("LEFT",
					childrenForProperty.get(startPos).getData().nodeKey);
		}
		if (endPos < childrenForProperty.size()) {
			multinodeSymbol.addAnnotation("RIGHT",
					childrenForProperty.get(endPos).getData().nodeKey);
		}
		final int multinodeId = baseGrammar.treeFormat
				.getOrAddSymbolId(multinodeSymbol);
		return multinodeId;
	}

	/**
	 * @param convertedTrees
	 * @param currentTo
	 * @param propertyId
	 * @param childrenForProperty
	 * @param multinodeNeedToBeAdded
	 * @param startPos
	 * @param endPos
	 * @param createMultinodes
	 */
	public void processMultinodes(
			final Multiset<TreeNode<TSGNode>> convertedTrees,
			final TreeNode<TSGNode> currentTo, final int propertyId,
			final List<TreeNode<TSGNode>> childrenForProperty,
			final boolean multinodeNeedToBeAdded, int startPos, int endPos,
			final boolean createMultinodes) {
		if (!multinodeNeedToBeAdded) {
			return;
		}

		// Create MULTINODE
		final int multinodeId = getMultinodeSymbolIdFor(childrenForProperty,
				startPos, endPos);

		// add it as a child to the current node, and
		// don't further visit.
		final TSGNode rootNode = new TSGNode(multinodeId);
		rootNode.isRoot = true;

		final TreeNode<TSGNode> multinode = TreeNode.create(rootNode, 2);
		currentTo.addChildNode(multinode, propertyId);

		// Add trees for all intermediate nodes
		if (createMultinodes) {
			if (startPos < 0) {
				startPos = 0;
			}
			if (endPos > childrenForProperty.size()) {
				endPos = childrenForProperty.size();
			}
			for (int i = startPos; i < endPos; i++) {
				final TreeNode<TSGNode> fromMultinode = createMultinodeTree(
						childrenForProperty, rootNode, i);
				convertedTrees.add(fromMultinode);
			}

			// Now also add an empty
			final TreeNode<TSGNode> nullMultinode = TreeNode
					.create(rootNode, 2);
			convertedTrees.add(nullMultinode);
		}

	}

	@Override
	public void prune(final int threshold) {
		baseGrammar.prune(threshold);
	}

	@Override
	public boolean removeTree(final TreeNode<TSGNode> root) {
		boolean result = true;
		for (final TreeNode<TSGNode> tree : convertTreeNode(root, true)) {
			result &= baseGrammar.removeTree(tree);
		}
		return result;
	}

	@Override
	public String toString() {
		return baseGrammar.toString();
	}

	@Override
	public String treeToString(final TreeNode<TSGNode> tree) {
		return baseGrammar.treeToString(tree);
	}

}

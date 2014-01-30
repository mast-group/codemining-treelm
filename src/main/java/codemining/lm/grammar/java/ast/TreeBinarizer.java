/**
 * 
 */
package codemining.lm.grammar.java.ast;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.List;

import codemining.lm.grammar.tree.ASTNodeSymbol;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;

/**
 * A utility class for binarizing-debinarizing trees. Trees are also markovized.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class TreeBinarizer implements Serializable {

	private static final long serialVersionUID = -3866555049892645508L;

	private final boolean annotateMultinodes;

	private final AbstractJavaTreeExtractor extractor;

	public TreeBinarizer(final AbstractJavaTreeExtractor ex) {
		extractor = ex;
		annotateMultinodes = true;
	}

	public TreeBinarizer(final AbstractJavaTreeExtractor ex,
			final boolean annotateMultinodes) {
		extractor = ex;
		this.annotateMultinodes = annotateMultinodes;
	}

	/**
	 * Binarize a single tree.
	 * 
	 * @param fromTree
	 * @return
	 */
	public TreeNode<Integer> binarizeTree(final TreeNode<Integer> fromTree) {
		final TreeNode<Integer> toTree = TreeNode.create(fromTree.getData(),
				fromTree.nProperties());
		final ArrayDeque<TreeNode<Integer>> toStack = new ArrayDeque<TreeNode<Integer>>();
		final ArrayDeque<TreeNode<Integer>> fromStack = new ArrayDeque<TreeNode<Integer>>();

		toStack.push(toTree);
		fromStack.push(fromTree);

		while (!toStack.isEmpty()) {
			final TreeNode<Integer> currentTo = toStack.pop();
			final TreeNode<Integer> currentFrom = fromStack.pop();

			final List<List<TreeNode<Integer>>> children = currentFrom
					.getChildrenByProperty();

			for (int i = 0; i < children.size(); i++) {
				final List<TreeNode<Integer>> childrenForProperty = children
						.get(i);
				if (childrenForProperty.size() <= 2) {
					for (final TreeNode<Integer> fromChild : childrenForProperty) {
						final TreeNode<Integer> toChild = TreeNode.create(
								fromChild.getData(), fromChild.nProperties());
						currentTo.addChildNode(toChild, i);
						toStack.push(toChild);
						fromStack.push(fromChild);
					}
				} else {
					createMultinode(currentTo, currentFrom, toStack, fromStack,
							i);
				}
			}
		}

		return toTree;
	}

	/**
	 * Create a tree of multinodes, binarizing other nodes.
	 * 
	 * @param currentTo
	 * @param currentFrom
	 * @param toStack
	 * @param fromStack
	 * @param propertyId
	 */
	private void createMultinode(final TreeNode<Integer> currentTo,
			final TreeNode<Integer> currentFrom,
			final ArrayDeque<TreeNode<Integer>> toStack,
			final ArrayDeque<TreeNode<Integer>> fromStack, final int propertyId) {
		final List<TreeNode<Integer>> childrenForProperty = currentFrom
				.getChildrenByProperty().get(propertyId);

		final TreeNode<Integer> child1From = childrenForProperty
				.get(childrenForProperty.size() - 1);
		TreeNode<Integer> child1To = TreeNode.create(child1From);

		final TreeNode<Integer> child2From = childrenForProperty
				.get(childrenForProperty.size() - 2);
		TreeNode<Integer> child2To = TreeNode.create(child2From);

		// Create the last node.
		TreeNode<Integer> currentTreeNode = TreeNode.create(extractor
				.getOrAddSymbolId(createMultinodeSymbol(child1From.getData())),
				2);

		// Watchout!! The order is important, for debinarizing!
		currentTreeNode.addChildNode(child2To, 0);
		fromStack.push(child2From);
		toStack.push(child2To);

		currentTreeNode.addChildNode(child1To, 0);
		fromStack.push(child1From);
		toStack.push(child1To);

		for (int i = childrenForProperty.size() - 3; i >= 0; i--) {
			final TreeNode<Integer> fromChild = childrenForProperty.get(i);
			final TreeNode<Integer> toChild = TreeNode.create(
					fromChild.getData(), fromChild.nProperties());

			final TreeNode<Integer> multiNode = TreeNode.create(extractor
					.getOrAddSymbolId(createMultinodeSymbol(toChild
							.getData())), 2);
			multiNode.addChildNode(currentTreeNode, 1); // Next nodes
			multiNode.addChildNode(toChild, 0); // Current node

			fromStack.push(fromChild);
			toStack.push(toChild);

			currentTreeNode = multiNode;
		}

		currentTo.addChildNode(currentTreeNode, propertyId);
	}

	private ASTNodeSymbol createMultinodeSymbol(final int type) {
		final ASTNodeSymbol multinode = new ASTNodeSymbol(
				ASTNodeSymbol.MULTI_NODE);
		multinode.addChildProperty("Current");
		multinode.addChildProperty("Next");
		if (annotateMultinodes) {
			multinode.addAnnotation("Type", type);
		}
		return multinode;
	}

	/**
	 * Given a binary tree, debinarize it.
	 * 
	 * @param fromTree
	 * @return
	 */
	public TreeNode<Integer> debinarize(final TreeNode<Integer> fromTree) {
		final ASTNodeSymbol rootSymbol = extractor
				.getSymbol(fromTree.getData());
		checkArgument(rootSymbol.nodeType != ASTNodeSymbol.MULTI_NODE);

		final TreeNode<Integer> toTree = TreeNode.create(fromTree);

		final ArrayDeque<TreeNode<Integer>> toStack = new ArrayDeque<TreeNode<Integer>>();
		final ArrayDeque<TreeNode<Integer>> fromStack = new ArrayDeque<TreeNode<Integer>>();

		toStack.push(toTree);
		fromStack.push(fromTree);

		while (!toStack.isEmpty()) {
			final TreeNode<Integer> currentTo = toStack.pop();
			final TreeNode<Integer> currentFrom = fromStack.pop();

			final List<List<TreeNode<Integer>>> children = currentFrom
					.getChildrenByProperty();

			for (int i = 0; i < children.size(); i++) {
				final List<TreeNode<Integer>> childrenForProperty = children
						.get(i);

				for (final TreeNode<Integer> fromChild : childrenForProperty) {
					ASTNodeSymbol symbol = extractor.getSymbol(fromChild
							.getData());

					if (symbol.nodeType == ASTNodeSymbol.MULTI_NODE) {
						TreeNode<Integer> currentChild = fromChild;

						while (currentChild != null
								&& symbol.nodeType == ASTNodeSymbol.MULTI_NODE) {
							for (final TreeNode<Integer> child : currentChild
									.getChildrenByProperty().get(0)) {

								final TreeNode<Integer> toChild = TreeNode
										.create(child.getData(),
												child.nProperties());
								currentTo.addChildNode(toChild, i);
								toStack.push(toChild);
								fromStack.push(child);
							}
							if (currentChild.getChildrenByProperty().get(1)
									.isEmpty()) {
								currentChild = null;
							} else {
								currentChild = currentChild
										.getChildrenByProperty().get(1).get(0);
								symbol = extractor.getSymbol(currentChild
										.getData());
							}
						}
					} else {
						final TreeNode<Integer> toChild = TreeNode
								.create(fromChild);
						currentTo.addChildNode(toChild, i);
						toStack.push(toChild);
						fromStack.push(fromChild);
					}
				}
			}
		}

		return toTree;
	}
}

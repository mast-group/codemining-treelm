/**
 * 
 */
package codemining.lm.grammar.tsg.samplers.blocked;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;

import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;

import com.google.common.collect.Sets;

/**
 * A collapsed Gibbs sampler.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class FilteredBlockCollapsedGibbsSampler extends
		BlockCollapsedGibbsSampler {

	private static final long serialVersionUID = -4108971545978001532L;

	private final AbstractJavaTreeExtractor treeExtractor;

	Set<TreeNode<TSGNode>> unbreakableNodes = Sets.newIdentityHashSet();

	public FilteredBlockCollapsedGibbsSampler(final double avgTreeSize,
			final double DPconcentration,
			final JavaFormattedTSGrammar sampleGrammar,
			final JavaFormattedTSGrammar allSamplesGrammar) {
		super(avgTreeSize, DPconcentration, sampleGrammar, allSamplesGrammar);
		treeExtractor = sampleGrammar.getJavaTreeExtractor();
	}

	@Override
	public TreeNode<TSGNode> addTree(final TreeNode<TSGNode> tree,
			final boolean forceAdd) {
		final TreeNode<TSGNode> filteredTree = filterTreeAndAddToUnbreakable(tree);
		if (filteredTree == null) {
			return null;
		}
		return super.addTree(filteredTree, forceAdd);
	}

	/**
	 * Filter a tree to include the right nodes at the right states.
	 * 
	 * @param tree
	 * @return
	 */
	private TreeNode<TSGNode> filterTreeAndAddToUnbreakable(
			final TreeNode<TSGNode> tree) {
		final TreeNode<TSGNode> currentTree;
		if (treeExtractor.getSymbol(tree.getData().nodeKey).nodeType == ASTNode.COMPILATION_UNIT) {
			if (tree.getChildrenByProperty().get(2).isEmpty()) {
				return null;
			}
			currentTree = tree.getChild(0, 2);
			currentTree.getData().isRoot = true;
		} else {
			currentTree = tree;
		}

		final Deque<TreeNode<TSGNode>> stack = new ArrayDeque<TreeNode<TSGNode>>();
		stack.push(currentTree);

		while (!stack.isEmpty()) {
			final TreeNode<TSGNode> currentNode = stack.pop();
			final TSGNode data = currentNode.getData();

			final List<List<TreeNode<TSGNode>>> currentNodeChildren = currentNode
					.getChildrenByProperty();
			for (int i = 0, size = currentNodeChildren.size(); i < size; i++) {
				final List<TreeNode<TSGNode>> childrenForProperty = currentNodeChildren
						.get(i);
				for (final TreeNode<TSGNode> child : childrenForProperty) {
					final TSGNode childData = child.getData();
					if (!isSplittable(childData, data)) {
						childData.isRoot = false;
						unbreakableNodes.add(child);
					}
					stack.push(child);
				}
			}
		}

		return currentTree;
	}

	private boolean isSplittable(final TSGNode node, final TSGNode parent) {
		final int nodeType = treeExtractor.getSymbol(node.nodeKey).nodeType;
		final int parentType = treeExtractor.getSymbol(parent.nodeKey).nodeType;

		if (parentType == ASTNode.METHOD_INVOCATION) {
			return false;
		}
		if (parentType == ASTNode.QUALIFIED_TYPE) {
			return false;
		}
		if (parentType == ASTNode.PARAMETERIZED_TYPE) {
			return false;
		}
		if ((parentType == ASTNode.FOR_STATEMENT || parentType == ASTNode.ENHANCED_FOR_STATEMENT)
				&& nodeType != ASTNode.BLOCK) {
			return false;
		}
		if (parentType == ASTNode.VARIABLE_DECLARATION_STATEMENT) {
			return false;
		}
		if (parentType == ASTNode.INFIX_EXPRESSION
				|| parentType == ASTNode.POSTFIX_EXPRESSION) {
			return false;
		}
		if (parentType == ASTNode.MARKER_ANNOTATION) {
			return false;
		}
		if (parentType == ASTNode.IF_STATEMENT && nodeType != ASTNode.BLOCK) {
			return false;
		}
		if (parentType == ASTNode.METHOD_DECLARATION
				&& nodeType == ASTNode.SINGLE_VARIABLE_DECLARATION) {
			return false;
		}
		if (parentType == ASTNode.PARENTHESIZED_EXPRESSION) {
			return false;
		}
		return true;
	}

	@Override
	public void sampleAt(final TreeNode<TSGNode> node) {
		if (!unbreakableNodes.contains(node)) {
			super.sampleAt(node);
		}
	}

}

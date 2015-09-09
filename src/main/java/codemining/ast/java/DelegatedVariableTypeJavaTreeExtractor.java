/**
 *
 */
package codemining.ast.java;

import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.SimpleName;

import codemining.ast.AstNodeSymbol;
import codemining.ast.TreeNode;

/**
 * A delegated variable type tree extractor. It adds an extra Object node
 * between the template node and the actual type, allowing for some flexibility
 * in mining.
 *
 * @author Miltos Allamans <m.allamanis@ed.ac.uk>
 *
 */
public class DelegatedVariableTypeJavaTreeExtractor extends
VariableTypeJavaTreeExtractor {

	public class DelegatedTypeJavaTreeExtractor extends
	VariableTypeTreeExtractor {

		private static final String GENERIC_VARIABLE_TYPE = "%GenericVariable%";

		public DelegatedTypeJavaTreeExtractor(final ASTNode extracted,
				final boolean useComments) {
			super(extracted, useComments);
		}

		@Override
		protected TreeNode<Integer> getTempletizedSubtreeForNode(
				final SimpleName node, final TreeNode<Integer> treeNode) {
			final TreeNode<Integer> templetized = super
					.getTempletizedSubtreeForNode(node, treeNode);
			if (isVariableSymbol(getSymbol(templetized.getData()))) {
				final AstNodeSymbol symbol = constructTypeSymbol(GENERIC_VARIABLE_TYPE);
				final TreeNode<Integer> variableTemplatedTree = TreeNode
						.create(getOrAddSymbolId(symbol), 1);
				variableTemplatedTree.addChildNode(templetized, 0);
				return variableTemplatedTree;
			} else {
				return templetized;
			}
		}
	}

	private static final long serialVersionUID = -9156945128462823330L;

	@Override
	public TreeNode<Integer> getTree(final ASTNode node,
			final boolean useComments) {
		final DelegatedTypeJavaTreeExtractor ex = new DelegatedTypeJavaTreeExtractor(
				node, useComments);
		ex.extractFromNode(node);
		return ex.computedNodes.get(node);
	}

	@Override
	public Map<ASTNode, TreeNode<Integer>> getTreeMap(final ASTNode node) {
		final DelegatedTypeJavaTreeExtractor ex = new DelegatedTypeJavaTreeExtractor(
				node, false);
		ex.extractFromNode(node);
		return ex.computedNodes;
	}

}

/**
 *
 */
package codemining.lm.grammar.js.ast;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.wst.jsdt.core.dom.ASTNode;
import org.eclipse.wst.jsdt.core.dom.ASTVisitor;
import org.eclipse.wst.jsdt.core.dom.Assignment;
import org.eclipse.wst.jsdt.core.dom.InfixExpression;
import org.eclipse.wst.jsdt.core.dom.JavaScriptUnit;
import org.eclipse.wst.jsdt.core.dom.Modifier;
import org.eclipse.wst.jsdt.core.dom.PostfixExpression;
import org.eclipse.wst.jsdt.core.dom.PrefixExpression;
import org.eclipse.wst.jsdt.core.dom.PrimitiveType;
import org.eclipse.wst.jsdt.core.dom.SimplePropertyDescriptor;
import org.eclipse.wst.jsdt.core.dom.StructuralPropertyDescriptor;

import codemining.js.codeutils.JavascriptASTExtractor;
import codemining.js.codeutils.JavascriptTokenizer;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.ParseType;
import codemining.lm.grammar.tree.AbstractTreeExtractor;
import codemining.lm.grammar.tree.AstNodeSymbol;
import codemining.lm.grammar.tree.TreeNode;

import com.google.common.base.Function;
import com.google.common.collect.BiMap;
import com.google.common.collect.Maps;

/**
 * A JavaScript tree extractor
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class JavascriptTreeExtractor extends AbstractTreeExtractor {

	/**
	 * Extract a TreeNode from a parsed AST
	 *
	 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
	 *
	 */
	public class TreeNodeExtractor extends ASTVisitor {

		/**
		 * Store all the TreeNodes that have been extracted.
		 */
		final Map<ASTNode, TreeNode<Integer>> computedNodes = Maps.newHashMap();

		public TreeNodeExtractor(final boolean useComments) {
			super(useComments);
		}

		public void extractFromNode(final ASTNode node) {
			node.accept(this);
		}

		public TreeNode<Integer> postProcessNodeBeforeAdding(
				final TreeNode<Integer> treeNode, final ASTNode node) {
			// Useful for subclasses, implementing more specific behaviors.
			return treeNode;
		}

		@Override
		public void postVisit(final ASTNode node) {
			try {
				final AstNodeSymbol symbol = new AstNodeSymbol(
						node.getNodeType());
				final List<StructuralPropertyDescriptor> supportedDescriptors = node
						.structuralPropertiesForType();

				// Add simple properties
				final List<SimplePropertyDescriptor> simpleDescriptors = JavascriptAstPropertiesData
						.getSimpleProperties(node.getNodeType());
				for (int i = 0; i < simpleDescriptors.size(); i++) {
					final SimplePropertyDescriptor sp = simpleDescriptors
							.get(i);
					if (!supportedDescriptors.contains(sp)) {
						continue;
					}
					final Object structuralProperty = node
							.getStructuralProperty(sp);
					if (structuralProperty == null) {
						continue;
					}
					addSimplePropertyToSymbol(symbol, sp, structuralProperty);
				}

				// Add child properties to symbol
				final List<StructuralPropertyDescriptor> descriptors = JavascriptAstPropertiesData
						.getChildProperties(node.getNodeType());
				for (final StructuralPropertyDescriptor descriptor : descriptors) {
					symbol.addChildProperty(descriptor.getId());
				}

				annotateSymbol(symbol, node);
				final int symbolId = getOrAddSymbolId(symbol);

				final TreeNode<Integer> treeNode = TreeNode.create(symbolId,
						descriptors.size());

				for (int i = 0; i < descriptors.size(); i++) {
					if (!supportedDescriptors.contains(descriptors.get(i))) {
						continue;
					}
					if (descriptors.get(i).isChildProperty()) {
						final ASTNode child = (ASTNode) node
								.getStructuralProperty(descriptors.get(i));
						if (child == null) {
							continue;
						}
						treeNode.addChildNode(
								checkNotNull(computedNodes.get(child)), i);
					} else {
						// is child list
						final List<ASTNode> children = (List<ASTNode>) node
								.getStructuralProperty(descriptors.get(i));
						if (children == null) {
							continue;
						}
						for (final ASTNode child : children) {
							treeNode.addChildNode(
									checkNotNull(computedNodes.get(child)), i);
						}
					}
				}

				computedNodes.put(node,
						postProcessNodeBeforeAdding(treeNode, node));
			} catch (Exception e) {
				LOGGER.warning("Failed to get Tree for node and children"
						+ node + ExceptionUtils.getFullStackTrace(e));
			}
		}
	}

	/**
	 * Add a simple property to a single symbol.
	 *
	 * @param symbol
	 * @param sp
	 * @param spValue
	 */
	protected static void addSimplePropertyToSymbol(final AstNodeSymbol symbol,
			final StructuralPropertyDescriptor sp, final Object spValue) {
		if (spValue instanceof Modifier.ModifierKeyword) {
			final Modifier.ModifierKeyword mod = (Modifier.ModifierKeyword) spValue;
			symbol.addSimpleProperty(sp.getId(), mod.toString());
		} else if (spValue instanceof PrimitiveType.Code) {
			final PrimitiveType.Code type = (PrimitiveType.Code) spValue;
			symbol.addSimpleProperty(sp.getId(), type.toString());
		} else if (spValue instanceof Assignment.Operator) {
			final Assignment.Operator op = (Assignment.Operator) spValue;
			symbol.addSimpleProperty(sp.getId(), op.toString());
		} else if (spValue instanceof InfixExpression.Operator) {
			final InfixExpression.Operator op = (InfixExpression.Operator) spValue;
			symbol.addSimpleProperty(sp.getId(), op.toString());
		} else if (spValue instanceof PrefixExpression.Operator) {
			final PrefixExpression.Operator op = (PrefixExpression.Operator) spValue;
			symbol.addSimpleProperty(sp.getId(), op.toString());
		} else if (spValue instanceof PostfixExpression.Operator) {
			final PostfixExpression.Operator op = (PostfixExpression.Operator) spValue;
			symbol.addSimpleProperty(sp.getId(), op.toString());
		} else {
			symbol.addSimpleProperty(sp.getId(), spValue);
		}
	}

	private static final long serialVersionUID = 4837298262921891190L;

	private static final Logger LOGGER = Logger
			.getLogger(JavascriptTreeExtractor.class.getName());

	public static final Function<Integer, String> JAVASCRIPT_NODETYPE_CONVERTER = new Function<Integer, String>() {

		@Override
		public String apply(Integer nodeType) {
			try {
				return ASTNode.nodeClassForType(nodeType).getSimpleName();
			} catch (Exception e) {
				return "Unknown Node Type " + nodeType;
			}
		}
	};

	/**
	 * A node printer using the symbols.
	 */
	private final Function<TreeNode<Integer>, String> javascriptNodeToString = new Function<TreeNode<Integer>, String>() {

		@Override
		public String apply(final TreeNode<Integer> node) {
			return getSymbol(node.getData()).toString(
					JAVASCRIPT_NODETYPE_CONVERTER);
		}

	};

	public JavascriptTreeExtractor() {
		super();
	}

	public JavascriptTreeExtractor(BiMap<Integer, AstNodeSymbol> alphabet) {
		super(alphabet);
	}

	/**
	 * Add further annotations to the given symbol. Useful for classes that will
	 * subclass this one.
	 *
	 * @param symbol
	 * @param node
	 */
	public void annotateSymbol(final AstNodeSymbol symbol, final ASTNode node) {
		// Do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tree.AbstractTreeExtractor#getCodeFromTree(codemining
	 * .lm.grammar.tree.TreeNode)
	 */
	@Override
	public String getCodeFromTree(TreeNode<Integer> tree) {
		throw new NotImplementedException();
		// TODO
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tree.AbstractTreeExtractor#getKeyForCompilationUnit
	 * ()
	 */
	@Override
	public TreeNode<Integer> getKeyForCompilationUnit() {
		for (final Entry<Integer, AstNodeSymbol> entry : nodeAlphabet
				.entrySet()) {
			if (entry.getValue().nodeType == ASTNode.JAVASCRIPT_UNIT) {
				return TreeNode.create(entry.getKey(), entry.getValue()
						.nChildProperties());
			}
		}
		throw new IllegalStateException(
				"A compilation unit must have been here...");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.grammar.tree.AbstractTreeExtractor#getTokenizer()
	 */
	@Override
	public ITokenizer getTokenizer() {
		return new JavascriptTokenizer();
	}

	public TreeNode<Integer> getTree(final ASTNode node) {
		final TreeNodeExtractor ex = new TreeNodeExtractor(false);
		ex.extractFromNode(node);
		return ex.computedNodes.get(node);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tree.AbstractTreeExtractor#getTree(java.io.File)
	 */
	@Override
	public TreeNode<Integer> getTree(File f) throws IOException {
		final JavascriptASTExtractor ex = new JavascriptASTExtractor(false);
		final JavaScriptUnit root = ex.getAST(f);
		return getTree(root);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tree.AbstractTreeExtractor#getTree(java.lang.String
	 * , codemining.languagetools.ParseType)
	 */
	@Override
	public TreeNode<Integer> getTree(final String code,
			final ParseType parseType) {
		final JavascriptASTExtractor ex = new JavascriptASTExtractor(false);
		ASTNode root = ex.getAST(code, parseType);
		return getTree(root);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see codemining.lm.grammar.tree.AbstractTreeExtractor#getTreePrinter()
	 */
	@Override
	public Function<TreeNode<Integer>, String> getTreePrinter() {
		return javascriptNodeToString;
	}

}

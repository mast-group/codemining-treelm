/**
 *
 */
package codemining.lm.grammar.js.ast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.wst.jsdt.core.dom.AST;
import org.eclipse.wst.jsdt.core.dom.ASTNode;
import org.eclipse.wst.jsdt.core.dom.ASTVisitor;
import org.eclipse.wst.jsdt.core.dom.Assignment;
import org.eclipse.wst.jsdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.wst.jsdt.core.dom.ChildPropertyDescriptor;
import org.eclipse.wst.jsdt.core.dom.InfixExpression;
import org.eclipse.wst.jsdt.core.dom.JavaScriptUnit;
import org.eclipse.wst.jsdt.core.dom.Modifier;
import org.eclipse.wst.jsdt.core.dom.Modifier.ModifierKeyword;
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
import com.google.common.collect.Lists;
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

	protected static void addSimplePropertyToASTNode(final ASTNode node,
			final SimplePropertyDescriptor sp, final Object spValue)
					throws Exception {
		checkNotNull(sp, "sp should not be null");
		checkNotNull(spValue);
		if (node instanceof Modifier) {
			final Modifier.ModifierKeyword mod = Modifier.ModifierKeyword
					.toKeyword((String) spValue);
			node.setStructuralProperty(sp, mod);
		} else if (node instanceof PrimitiveType) {
			final String type = (String) spValue;
			final PrimitiveType.Code typeCode;
			if (spValue.equals("boolean")) {
				typeCode = PrimitiveType.BOOLEAN;
			} else if (spValue.equals("byte")) {
				typeCode = PrimitiveType.BYTE;
			} else if (spValue.equals("char")) {
				typeCode = PrimitiveType.CHAR;
			} else if (spValue.equals("double")) {
				typeCode = PrimitiveType.DOUBLE;
			} else if (spValue.equals("float")) {
				typeCode = PrimitiveType.FLOAT;
			} else if (spValue.equals("int")) {
				typeCode = PrimitiveType.INT;
			} else if (spValue.equals("long")) {
				typeCode = PrimitiveType.LONG;
			} else if (spValue.equals("short")) {
				typeCode = PrimitiveType.SHORT;
			} else if (spValue.equals("void")) {
				typeCode = PrimitiveType.VOID;
			} else {
				LOGGER.severe("could not find primitive type " + type);
				throw new Exception("could not find primitive type " + type);
			}
			node.setStructuralProperty(sp, typeCode);
		} else if (node instanceof Assignment) {
			final Assignment.Operator op = Assignment.Operator
					.toOperator((String) spValue);
			node.setStructuralProperty(sp, op);
		} else if (node instanceof InfixExpression) {
			final InfixExpression.Operator op = InfixExpression.Operator
					.toOperator((String) spValue);
			node.setStructuralProperty(sp, op);
		} else if (node instanceof PrefixExpression) {
			final PrefixExpression.Operator op = PrefixExpression.Operator
					.toOperator((String) spValue);
			node.setStructuralProperty(sp, op);
		} else if (node instanceof PostfixExpression) {
			final PostfixExpression.Operator op = PostfixExpression.Operator
					.toOperator((String) spValue);
			node.setStructuralProperty(sp, op);
		} else {
			node.setStructuralProperty(sp, spValue);
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
		} else if (spValue instanceof String) {
			final String stValue = (String) spValue;
			if (stValue.substring(0, 1).equals("'")
					&& stValue.substring(stValue.length() - 1).equals("'")) {
				symbol.addSimpleProperty(sp.getId(),
						"\"" + stValue.substring(1, stValue.length() - 1)
						+ "\"");
			} else {
				symbol.addSimpleProperty(sp.getId(), spValue);
			}

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

	private ASTNode createASTNodeObject(final TreeNode<Integer> treeNode,
			final AST ast, final AstNodeSymbol symbol) throws Exception {
		switch (symbol.nodeType) {
		case ASTNode.ANONYMOUS_CLASS_DECLARATION:
			return ast.newAnonymousClassDeclaration();
		case ASTNode.ARRAY_ACCESS:
			return ast.newArrayAccess();
		case ASTNode.ARRAY_CREATION:
			return ast.newArrayCreation();
		case ASTNode.ARRAY_INITIALIZER:
			return ast.newArrayInitializer();
		case ASTNode.ARRAY_TYPE:
			return ast.newArrayType(ast
					.newPrimitiveType(PrimitiveType.ANY_CODE));
		case ASTNode.ASSIGNMENT:
			return ast.newAssignment();
		case ASTNode.BLOCK:
			return ast.newBlock();
		case ASTNode.BLOCK_COMMENT:
			return ast.newBlockComment();
		case ASTNode.BOOLEAN_LITERAL:
			return ast.newBooleanLiteral(false);
		case ASTNode.BREAK_STATEMENT:
			return ast.newBreakStatement();
		case ASTNode.CATCH_CLAUSE:
			return ast.newCatchClause();
		case ASTNode.CHARACTER_LITERAL:
			return ast.newCharacterLiteral();
		case ASTNode.CLASS_INSTANCE_CREATION:
			return ast.newClassInstanceCreation();
		case ASTNode.CONDITIONAL_EXPRESSION:
			return ast.newConditionalExpression();
		case ASTNode.CONSTRUCTOR_INVOCATION:
			return ast.newConstructorInvocation();
		case ASTNode.CONTINUE_STATEMENT:
			return ast.newContinueStatement();
		case ASTNode.DO_STATEMENT:
			return ast.newDoStatement();
		case ASTNode.EMPTY_EXPRESSION:
			// An empty statement is an empty expression
			return ast.newEmptyStatement();
		case ASTNode.EMPTY_STATEMENT:
			return ast.newEmptyStatement();
		case ASTNode.ENHANCED_FOR_STATEMENT:
			return ast.newEnhancedForStatement();
		case ASTNode.EXPRESSION_STATEMENT:
			return ast.newExpressionStatement(ast.newCharacterLiteral());
		case ASTNode.FIELD_ACCESS:
			return ast.newFieldAccess();
		case ASTNode.FIELD_DECLARATION:
			return ast
					.newFieldDeclaration(ast.newVariableDeclarationFragment());
		case ASTNode.FOR_IN_STATEMENT:
			return ast.newForInStatement();
		case ASTNode.FOR_STATEMENT:
			return ast.newForStatement();
		case ASTNode.FUNCTION_DECLARATION:
			return ast.newFunctionDeclaration();
		case ASTNode.FUNCTION_EXPRESSION:
			return ast.newFunctionExpression();
		case ASTNode.FUNCTION_INVOCATION:
			return ast.newFunctionInvocation();
		case ASTNode.FUNCTION_REF:
			return ast.newFunctionRef();
		case ASTNode.FUNCTION_REF_PARAMETER:
			return ast.newFunctionRefParameter();
		case ASTNode.IF_STATEMENT:
			return ast.newIfStatement();
		case ASTNode.IMPORT_DECLARATION:
			return ast.newImportDeclaration();
		case ASTNode.INFERRED_TYPE:
			return ast.newInferredType("");
		case ASTNode.INFIX_EXPRESSION:
			return ast.newInfixExpression();
		case ASTNode.INITIALIZER:
			return ast.newInitializer();
		case ASTNode.INSTANCEOF_EXPRESSION:
			return ast.newInstanceofExpression();
		case ASTNode.JAVASCRIPT_UNIT:
			return ast.newJavaScriptUnit();
		case ASTNode.JSDOC:
			return ast.newJSdoc();
		case ASTNode.LABELED_STATEMENT:
			return ast.newLabeledStatement();
		case ASTNode.LINE_COMMENT:
			return ast.newLineComment();
		case ASTNode.LIST_EXPRESSION:
			return ast.newListExpression();
		case ASTNode.MEMBER_REF:
			return ast.newMemberRef();
		case ASTNode.MODIFIER:
			return ast.newModifier(ModifierKeyword.FINAL_KEYWORD);
		case ASTNode.NULL_LITERAL:
			return ast.newNullLiteral();
		case ASTNode.NUMBER_LITERAL:
			return ast.newNumberLiteral();
		case ASTNode.OBJECT_LITERAL:
			return ast.newObjectLiteral();
		case ASTNode.OBJECT_LITERAL_FIELD:
			return ast.newObjectLiteralField();
		case ASTNode.PACKAGE_DECLARATION:
			return ast.newPackageDeclaration();
		case ASTNode.PARENTHESIZED_EXPRESSION:
			return ast.newParenthesizedExpression();
		case ASTNode.POSTFIX_EXPRESSION:
			return ast.newPostfixExpression();
		case ASTNode.PREFIX_EXPRESSION:
			return ast.newPrefixExpression();
		case ASTNode.PRIMITIVE_TYPE:
			return ast.newPrimitiveType(PrimitiveType.ANY_CODE);
		case ASTNode.QUALIFIED_NAME:
			return ast.newQualifiedName(ast.newSimpleName("qmissing"),
					ast.newSimpleName("missing"));
		case ASTNode.QUALIFIED_TYPE:
			return ast.newQualifiedType(
					ast.newSimpleType(ast.newSimpleName("missingType")),
					ast.newSimpleName("missing"));
		case ASTNode.REGULAR_EXPRESSION_LITERAL:
			return ast.newRegularExpressionLiteral();
		case ASTNode.RETURN_STATEMENT:
			return ast.newReturnStatement();
		case ASTNode.SIMPLE_NAME:
			return ast.newSimpleName("missing");
		case ASTNode.SIMPLE_TYPE:
			return ast.newSimpleType(ast.newName("missing"));
		case ASTNode.SINGLE_VARIABLE_DECLARATION:
			return ast.newSingleVariableDeclaration();
		case ASTNode.STRING_LITERAL:
			return ast.newStringLiteral();
		case ASTNode.SUPER_CONSTRUCTOR_INVOCATION:
			return ast.newSuperConstructorInvocation();
		case ASTNode.SUPER_FIELD_ACCESS:
			return ast.newSuperFieldAccess();
		case ASTNode.SUPER_METHOD_INVOCATION:
			return ast.newSuperMethodInvocation();
		case ASTNode.SWITCH_CASE:
			return ast.newSwitchCase();
		case ASTNode.SWITCH_STATEMENT:
			return ast.newSwitchStatement();
		case ASTNode.TAG_ELEMENT:
			return ast.newTagElement();
		case ASTNode.TEXT_ELEMENT:
			return ast.newTextElement();
		case ASTNode.THIS_EXPRESSION:
			return ast.newThisExpression();
		case ASTNode.THROW_STATEMENT:
			return ast.newThrowStatement();
		case ASTNode.TRY_STATEMENT:
			return ast.newTryStatement();
		case ASTNode.TYPE_DECLARATION:
			return ast.newTypeDeclaration();
		case ASTNode.TYPE_DECLARATION_STATEMENT:
			return ast.newTypeDeclarationStatement(ast.newTypeDeclaration());
		case ASTNode.TYPE_LITERAL:
			return ast.newTypeLiteral();
		case ASTNode.UNDEFINED_LITERAL:
			return ast.newUndefinedLiteral();
		case ASTNode.VARIABLE_DECLARATION_EXPRESSION:
			return ast.newVariableDeclarationExpression(ast
					.newVariableDeclarationFragment());
		case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
			return ast.newVariableDeclarationFragment();
		case ASTNode.VARIABLE_DECLARATION_STATEMENT:
			return ast.newVariableDeclarationStatement(ast
					.newVariableDeclarationFragment());
		case ASTNode.WHILE_STATEMENT:
			return ast.newWhileStatement();
		case ASTNode.WITH_STATEMENT:
			return ast.newWithStatement();
		default:
			LOGGER.severe("Failed to find node for code " + treeNode.getData());
			throw new Exception("Unkown type of ASTNode");
		}
	}

	/**
	 * Create an AST from a given TreeNode
	 *
	 */
	public ASTNode getASTFromTree(final TreeNode<Integer> tree) {
		final Map<TreeNode<Integer>, ASTNode> extractedNodes = Maps
				.newIdentityHashMap();

		// Do a pre-order visit. Topological sorting
		final Deque<TreeNode<Integer>> toVisit = new ArrayDeque<TreeNode<Integer>>();
		// It will contain the nodes in the order from the last to be converted
		// to the first (i.e topologically sorted)
		final List<TreeNode<Integer>> conversionPlan = Lists.newArrayList();

		toVisit.push(tree);
		while (!toVisit.isEmpty()) {
			final TreeNode<Integer> node = toVisit.pop();
			conversionPlan.add(node);
			for (final List<TreeNode<Integer>> childProperty : node
					.getChildrenByProperty()) {
				for (final TreeNode<Integer> child : childProperty) {
					toVisit.push(child);
				}
			}
		}

		// OK. Now back to business...
		final AST ast = AST.newAST(AST.JLS3);
		for (int i = conversionPlan.size() - 1; i >= 0; i--) {
			try {
				final TreeNode<Integer> toBeConverted = conversionPlan.get(i);
				for (final List<TreeNode<Integer>> childProperties : toBeConverted
						.getChildrenByProperty()) {
					for (final TreeNode<Integer> child : childProperties) {
						checkArgument(extractedNodes.containsKey(child));
					}
				}
				getASTNodeForTreeNode(toBeConverted, ast, extractedNodes);
			} catch (Exception e) {
				LOGGER.warning("Failed to get ASTNode for subtree "
						+ e.getMessage() + " "
						+ ExceptionUtils.getFullStackTrace(e));
			}
		}

		return extractedNodes.get(tree);
	}

	/**
	 * Create a new ASTNode for the given symbol, setting only the simple
	 * properties.
	 *
	 * @param symbol
	 * @param ast
	 * @return
	 * @throws Exception
	 */
	private final ASTNode getASTNodeForTreeNode(
			final TreeNode<Integer> treeNode, final AST ast,
			final Map<TreeNode<Integer>, ASTNode> createdASTNodes)
					throws Exception {
		final AstNodeSymbol symbol = getSymbol(treeNode.getData());
		final ASTNode node = createASTNodeObject(treeNode, ast, symbol);

		// Set children properties
		final List<StructuralPropertyDescriptor> descriptors = JavascriptAstPropertiesData
				.getChildProperties(symbol.nodeType);
		checkArgument(descriptors.size() == treeNode.nProperties());
		for (int i = 0; i < descriptors.size(); i++) {
			if (treeNode.getChildrenByProperty().get(i).isEmpty()) {
				continue; // Nothing to do.
			}

			if (descriptors.get(i) instanceof ChildPropertyDescriptor) {
				final TreeNode<Integer> child = treeNode
						.getChildrenByProperty().get(i).get(0);
				node.setStructuralProperty(descriptors.get(i),
						checkNotNull(createdASTNodes.get(child)));
			} else {
				checkArgument(descriptors.get(i) instanceof ChildListPropertyDescriptor);
				final List<ASTNode> nodesChildren = (List<ASTNode>) node
						.getStructuralProperty(descriptors.get(i));
				nodesChildren.clear();
				for (final TreeNode<Integer> childNode : treeNode
						.getChildrenByProperty().get(i)) {
					final ASTNode childAst = checkNotNull(createdASTNodes
							.get(childNode));
					nodesChildren.add(childAst);
				}
			}
		}

		// Set simple properties
		for (final SimplePropertyDescriptor sp : JavascriptAstPropertiesData
				.getSimpleProperties(symbol.nodeType)) {
			final Object simplePropertyValue = symbol.getSimpleProperty(sp
					.getId());
			if (simplePropertyValue == null) {
				continue;
			}
			addSimplePropertyToASTNode(node, sp, simplePropertyValue);
		}

		createdASTNodes.put(treeNode, checkNotNull(node));
		return node;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * codemining.lm.grammar.tree.AbstractTreeExtractor#getCodeFromTree(codemining
	 * .lm.grammar.tree.TreeNode)
	 */
	@Override
	public String getCodeFromTree(final TreeNode<Integer> tree) {
		final ASTNode astTree = getASTFromTree(tree);
		// TODO: Do something more clever
		return astTree.toString();
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

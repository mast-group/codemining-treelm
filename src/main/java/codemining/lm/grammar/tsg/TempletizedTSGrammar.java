/**
 * 
 */
package codemining.lm.grammar.tsg;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayDeque;
import java.util.Map;

import codemining.lm.grammar.java.ast.TempletizedJavaTreeExtractor;
import codemining.lm.grammar.tree.ASTNodeSymbol;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.TSGNode.CopyPair;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.collect.Maps;

/**
 * A tree substitution grammar with variable-templetized rules. Works only with
 * VariableTempletizedTreeFormat.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
@DefaultSerializer(JavaSerializer.class)
public class TempletizedTSGrammar extends JavaFormattedTSGrammar {

	private static final long serialVersionUID = -6073281438936511225L;

	/**
	 * @param format
	 */
	public TempletizedTSGrammar(final AbstractJavaTreeExtractor format) {
		super(checkNotNull(format));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tsg.TSGrammar#addTree(codemining.lm.grammar.tree
	 * .TreeNode)
	 */
	@Override
	public void addTree(final TreeNode<TSGNode> subTree) {
		final TreeNode<TSGNode> reparametrizedTree = reparametrizeTree(subTree);
		super.addTree(reparametrizedTree);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tsg.TSGrammar#countTreeOccurences(codemining.lm
	 * .grammar.tree.TreeNode)
	 */
	@Override
	public int countTreeOccurences(final TreeNode<TSGNode> root) {
		return super.countTreeOccurences(reparametrizeTree(root));
	}

	@Override
	public int countTreesWithRoot(final TSGNode root) {
		final ASTNodeSymbol originalSymbol = treeFormat.getSymbol(root.nodeKey);
		if (originalSymbol.nodeType == ASTNodeSymbol.TEMPLATE_NODE
				&& originalSymbol
						.hasAnnotation(TempletizedJavaTreeExtractor.TEMPLETIZED_VAR_PROPERTY)) {
			final ASTNodeSymbol newSymbol = TempletizedJavaTreeExtractor
					.constructTemplateSymbol(
							0,
							(String) originalSymbol
									.getAnnotation(TempletizedJavaTreeExtractor.TEMPLETIZED_VAR_TYPE_PROPERTY));
			final TSGNode newNode = new TSGNode(
					treeFormat.getOrAddSymbolId(newSymbol));
			newNode.isRoot = root.isRoot;
			return super.countTreesWithRoot(newNode);
		} else {
			return super.countTreesWithRoot(root);
		}
	}

	/**
	 * Generate random tree from templetized TSG. This is harder from the
	 * general TSG because of the templetization that ties different variable
	 * ids together.
	 */
	@Override
	public TreeNode<TSGNode> generateRandom(final TreeNode<TSGNode> root) {
		throw new UnsupportedOperationException(
				"Cannot generate random tree from a templetized tsg, yet...");
		// We eventually need the following, generate the TSG as usual but we
		// also need
		// to reparametrize the tree, and we also need to tie the different
		// VAR_IDs
		// when thei are the same and generate a random symbol only once,
		// or if already generated (or in one TSG rule), reuse that one...
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tsg.TSGrammar#removeTree(codemining.lm.grammar.
	 * tree.TreeNode)
	 */
	@Override
	public boolean removeTree(final TreeNode<TSGNode> subTree) {
		return super.removeTree(reparametrizeTree(subTree));
	}

	/**
	 * Reparametrize a single tree, so that the VAR_ID's start from 0 in a
	 * pre-order visiting fashion.
	 * 
	 * @param tree
	 * @return
	 */
	public final TreeNode<TSGNode> reparametrizeTree(
			final TreeNode<TSGNode> tree) {
		int nextId = 0;
		// Contains a mapping of the ids of the old parametrized names to the
		// new parametrized names.
		final Map<Integer, Integer> oldNewMapping = Maps.newTreeMap();

		final TSGNode originalData = tree.getData();
		final ASTNodeSymbol originalSymbol = treeFormat
				.getSymbol(originalData.nodeKey);

		final TSGNode rootData;
		if (TempletizedJavaTreeExtractor.isTemplateVariable(originalSymbol)) {
			oldNewMapping
					.put((Integer) (originalSymbol
							.getAnnotation(TempletizedJavaTreeExtractor.TEMPLETIZED_VAR_PROPERTY)),
							0);
			final ASTNodeSymbol newSymbol = TempletizedJavaTreeExtractor
					.constructTemplateSymbol(
							0,
							(String) originalSymbol
									.getAnnotation(TempletizedJavaTreeExtractor.TEMPLETIZED_VAR_TYPE_PROPERTY));
			nextId++;
			rootData = new TSGNode(treeFormat.getOrAddSymbolId(newSymbol));
			rootData.isRoot = originalData.isRoot;
		} else {
			rootData = new TSGNode(originalData);
		}
		final TreeNode<TSGNode> reparamTree = TreeNode.create(rootData, tree
				.getChildrenByProperty().size());

		final ArrayDeque<CopyPair> stack = new ArrayDeque<CopyPair>();

		stack.push(new CopyPair(tree, reparamTree));

		while (!stack.isEmpty()) {
			final CopyPair pair = stack.pop();
			final TreeNode<TSGNode> currentFrom = pair.fromNode;
			final TreeNode<TSGNode> currentTo = pair.toNode;

			for (int i = 0; i < currentFrom.getChildrenByProperty().size(); i++) {
				for (final TreeNode<TSGNode> fromChild : currentFrom
						.getChildrenByProperty().get(i)) {
					final ASTNodeSymbol symbol = treeFormat.getSymbol(fromChild
							.getData().nodeKey);

					final TSGNode toChildData;
					if (TempletizedJavaTreeExtractor
							.isTemplateVariable(symbol)) {
						final int oldId = (Integer) symbol
								.getAnnotation(TempletizedJavaTreeExtractor.TEMPLETIZED_VAR_PROPERTY);

						Integer newId = oldNewMapping.get(oldId);
						if (newId == null) {
							newId = nextId;
							nextId++;
							oldNewMapping.put(oldId, newId);
						}
						final String typeProperty = (String) symbol
								.getAnnotation(TempletizedJavaTreeExtractor.TEMPLETIZED_VAR_TYPE_PROPERTY);
						final ASTNodeSymbol sym = TempletizedJavaTreeExtractor
								.constructTemplateSymbol(newId, typeProperty);
						toChildData = new TSGNode(
								treeFormat.getOrAddSymbolId(sym));
						toChildData.isRoot = fromChild.getData().isRoot;
					} else {
						toChildData = new TSGNode(fromChild.getData());
					}
					final TreeNode<TSGNode> toChild = TreeNode.create(
							toChildData, fromChild.nProperties());
					currentTo.addChildNode(toChild, i);

					stack.push(new CopyPair(fromChild, toChild));
				}
			}
		}

		return reparamTree;
	}
}

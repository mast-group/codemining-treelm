/**
 * 
 */
package codemining.lm.grammar.tsg;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map.Entry;

import org.eclipse.jdt.core.dom.ASTNode;

import codemining.lm.grammar.tree.ASTNodeSymbol;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.ITreeExtractor;
import codemining.lm.grammar.tree.TreeNode;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.base.Function;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;

/**
 * A TSG grammar that uses a tree format and an alphabet.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
@DefaultSerializer(JavaSerializer.class)
public class JavaFormattedTSGrammar extends TSGrammar<TSGNode> {

	/**
	 * A functional for converting a TSGNode to its string representation, given
	 * the format.
	 * 
	 */
	public final class IntKeyToSymbol implements
			Function<TreeNode<TSGNode>, String> {

		@Override
		public String apply(final TreeNode<TSGNode> node) {
			if (node == null || node.getData() == null) {
				return "UNK";
			}
			return treeFormat.getSymbol(checkNotNull(node).getData().nodeKey)
					.toString();
		}
	}

	private static final long serialVersionUID = 155850201795039891L;

	protected final AbstractJavaTreeExtractor treeFormat;

	/**
	 * @param format
	 */
	public JavaFormattedTSGrammar(final AbstractJavaTreeExtractor format) {
		super();
		treeFormat = format;
	}

	public final ASTNode generateRandom() {
		// Find compilation unit node.
		final TreeNode<Integer> rootNode = treeFormat
				.getKeyForCompilationUnit();

		final TSGNode topTreeNode = new TSGNode(rootNode.getData());
		topTreeNode.isRoot = true;

		final TreeNode<TSGNode> node = TreeNode.create(topTreeNode,
				rootNode.nProperties());

		final TreeNode<TSGNode> randomTree = this.generateRandom(node);

		final TreeNode<Integer> treeCopy = TreeNode.create(
				randomTree.getData().nodeKey, randomTree.nProperties());
		TSGNode.copyChildren(treeCopy, randomTree);
		return treeFormat.getASTFromTree(treeCopy);
	}

	public final AbstractJavaTreeExtractor getJavaTreeExtractor() {
		return treeFormat;
	}

	@Override
	public final ITreeExtractor<Integer> getTreeExtractor() {
		return treeFormat;
	}

	@Override
	public String toString() {
		final StringBuffer buf = new StringBuffer();
		for (final Entry<TSGNode, ConcurrentHashMultiset<TreeNode<TSGNode>>> rootEntry : grammar
				.entrySet()) {
			if (rootEntry.getValue().entrySet().isEmpty()) {
				continue;
			}
			final TSGNode root = rootEntry.getKey();
			buf.append("********\n");
			buf.append(treeFormat.getSymbol(root.nodeKey) + ":\n");
			for (final Multiset.Entry<TreeNode<TSGNode>> tree : Multisets
					.copyHighestCountFirst(rootEntry.getValue()).entrySet()) {
				final double prob = ((double) tree.getCount())
						/ rootEntry.getValue().size();
				buf.append("----------------------------------------\n");
				if (tree.getElement() != null) {
					if (tree.getElement().getData() != null) {
						buf.append(treeToString(tree.getElement()));
						buf.append("_________________________________\n");
						try {
							final TreeNode<Integer> intTree = TreeNode.create(
									tree.getElement().getData().nodeKey, tree
											.getElement().nProperties());
							TSGNode.copyChildren(intTree, tree.getElement());
							if (treeFormat.getSymbol(intTree.getData()).nodeType == ASTNodeSymbol.MULTI_NODE) {
								treeFormat.printMultinode(buf, intTree);
							} else {
								buf.append(treeFormat.getASTFromTree(intTree));
							}
						} catch (final Throwable e) {
							buf.append("Cannot get AST representation of rule");
						}
						buf.append("\n");
					} else {
						buf.append("null");
					}
				} else {
					buf.append("null");
				}

				buf.append(">Prob " + prob + " (" + tree.getCount() + ")\n");
			}
		}
		return buf.toString();
	}

	public String treeToString(final TreeNode<TSGNode> tree) {
		return tree.toString(new IntKeyToSymbol());
	}
}

/**
 *
 */
package codemining.lm.grammar.tsg.tui;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map.Entry;

import codemining.lm.grammar.tree.AbstractTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tree.TreeNode.NodePair;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.TSGrammar;
import codemining.util.parallel.ParallelThreadPool;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.collect.Multiset;

/**
 * Merge two tsgs into one.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class TsgMerger {

	private static int convert(final int id,
			final AbstractTreeExtractor fromFormat,
			final AbstractTreeExtractor toFormat) {
		return toFormat.getOrAddSymbolId(fromFormat.getSymbol(id));
	}

	public static TreeNode<Integer> convert(final TreeNode<Integer> fromNode,
			final AbstractTreeExtractor fromFormat,
			final AbstractTreeExtractor toFormat) {
		checkNotNull(fromNode);
		final ArrayDeque<NodePair<Integer>> stack = new ArrayDeque<NodePair<Integer>>();
		final TreeNode<Integer> toNode = TreeNode.create(
				convert(fromNode.getData(), fromFormat, toFormat),
				fromNode.nProperties());
		stack.push(new NodePair<Integer>(fromNode, toNode));
		while (!stack.isEmpty()) {
			final NodePair<Integer> pair = stack.pop();
			final TreeNode<Integer> currentFrom = pair.fromNode;
			final TreeNode<Integer> currentTo = pair.toNode;

			final List<List<TreeNode<Integer>>> children = currentFrom
					.getChildrenByProperty();
			for (int i = 0; i < children.size(); i++) {
				for (final TreeNode<Integer> fromChild : children.get(i)) {
					final TreeNode<Integer> toChild = TreeNode.create(
							convert(fromChild.getData(), fromFormat, toFormat),
							fromChild.nProperties());
					currentTo.addChildNode(toChild, i);
					stack.push(new NodePair<Integer>(fromChild, toChild));
				}
			}
		}

		return toNode;
	}

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 3) {
			System.err.println("Usage <tsg1> <tsg2> <to>");
			System.exit(-1);
		}

		final TSGrammar<TSGNode> tsg1 = (TSGrammar<TSGNode>) Serializer
				.getSerializer().deserializeFrom(args[0]);
		final AbstractTreeExtractor format1 = tsg1.getTreeExtractor();

		final TSGrammar<TSGNode> tsg2 = (TSGrammar<TSGNode>) Serializer
				.getSerializer().deserializeFrom(args[1]);
		final AbstractTreeExtractor format2 = tsg2.getTreeExtractor();

		checkArgument(format1.getClass().equals(format2.getClass()));

		final ParallelThreadPool ptp = new ParallelThreadPool();
		for (final Entry<TSGNode, ? extends Multiset<TreeNode<TSGNode>>> treeProd : tsg2
				.getInternalGrammar().entrySet()) {
			ptp.pushTask(new Runnable() {
				@Override
				public void run() {
					for (final Multiset.Entry<TreeNode<TSGNode>> tree : treeProd
							.getValue().entrySet()) {
						final TreeNode<Integer> intTree = TSGNode
								.tsgTreeToInt(tree.getElement());
						final TreeNode<Integer> converted = convert(intTree,
								format2, format1);
						tsg1.addTree(TSGNode.convertTree(converted, 0),
								tree.getCount());
					}
				}
			});

		}
		ptp.waitForTermination();

		Serializer.getSerializer().serialize(tsg1, args[2]);

	}

}

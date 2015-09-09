/**
 * 
 */
package codemining.ast;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.List;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;

/**
 * Compute the distance among two trees by computing the percent of overlapping
 * nodes.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class NodeSetTreeDistance<T extends Serializable> implements
		TreeDistance<T> {

	private static final long serialVersionUID = 2745567342952547961L;

	/**
	 * Static utility to return the distance.
	 * 
	 * @param tree1
	 * @param tree2
	 * @return
	 */
	public static <T extends Serializable> double distanceBetween(
			TreeNode<T> tree1, TreeNode<T> tree2) {
		final Multiset<T> nodesInTree1 = getAllData(tree1);
		final Multiset<T> nodesInTree2 = getAllData(tree2);
		final Multiset<T> nodesInBothTree2 = Multisets.intersection(
				nodesInTree1, nodesInTree2);
		double nCommon = nodesInBothTree2.size();
		double nAll = Multisets.union(nodesInTree1, nodesInTree2).size();
		return nCommon / nAll;
	}

	/**
	 * Return a multiset of all the data in the tree
	 * 
	 * @param tree
	 * @return
	 */
	private static <T extends Serializable> Multiset<T> getAllData(
			TreeNode<T> tree) {
		Multiset<T> data = HashMultiset.create();
		final ArrayDeque<TreeNode<T>> stack = new ArrayDeque<TreeNode<T>>();

		stack.push(tree);
		while (!stack.isEmpty()) {
			final TreeNode<T> current = stack.pop();
			data.add(current.getData());

			final List<List<TreeNode<T>>> children = current
					.getChildrenByProperty();
			for (List<TreeNode<T>> childrenForProperty : children) {
				for (final TreeNode<T> child : childrenForProperty) {
					stack.push(child);
				}
			}
		}
		return data;
	}

	@Override
	public double distance(TreeNode<T> tree1, TreeNode<T> tree2) {
		return distanceBetween(tree1, tree2);
	}

}

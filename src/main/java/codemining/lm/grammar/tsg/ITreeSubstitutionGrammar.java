package codemining.lm.grammar.tsg;

import java.io.Serializable;

import codemining.lm.grammar.tree.TreeNode;

import com.google.common.collect.SortedMultiset;

public interface ITreeSubstitutionGrammar<T extends Serializable> extends
		Serializable {

	/**
	 * Add a single tree to the grammar.
	 * 
	 * @param subTree
	 */
	public void addTree(TreeNode<T> subTree);

	/**
	 * Compute the distribution of the tree sizes in this grammar.
	 * 
	 * @return
	 */
	public SortedMultiset<Integer> computeGrammarTreeSizeStats();

	/**
	 * Compute the posterior probability for this tree, given the TSG.
	 * 
	 * @param tree
	 * @return
	 */
	public double computeTreePosteriorLog2Probability(TreeNode<T> tree);

	/**
	 * Return the number of occurrences of the subtree given the root.
	 * 
	 * @param root
	 * @return
	 */
	public int countTreeOccurences(TreeNode<T> root);

	/**
	 * Get the total number of possible occurrences for the given root.
	 * 
	 * @param root
	 * @return
	 */
	public int countTreesWithRoot(T root);

	/**
	 * Generate a random tree under root, given this TSG.
	 * 
	 * @param startKey
	 * @return
	 */
	public TreeNode<T> generateRandom(TreeNode<T> root);

	/**
	 * Remove a tree production.
	 * 
	 * @param subTree
	 * @return
	 */
	public boolean removeTree(TreeNode<T> subTree);

}
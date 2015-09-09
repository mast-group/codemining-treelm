package codemining.lm.tsg;

import java.io.Serializable;

import com.google.common.collect.SortedMultiset;

import codemining.ast.TreeNode;

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
	public double computeRulePosteriorLog2Probability(TreeNode<T> tree);

	/**
	 * Compute the posterior probability for this tree, given the TSG.
	 * 
	 * @param tree
	 * @return
	 */
	public double computeRulePosteriorLog2Probability(TreeNode<T> tree,
			boolean remove);

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
	 * @return true if a tree of this type was indeed removed
	 */
	public boolean removeTree(TreeNode<T> subTree);

	/**
	 * Remove a tree production.
	 * 
	 * @param subTree
	 * @param occurences
	 * @return the number of trees that were removed
	 */
	public int removeTree(TreeNode<T> subTree, int occurences);

}
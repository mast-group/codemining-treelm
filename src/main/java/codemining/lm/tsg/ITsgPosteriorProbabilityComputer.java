/**
 * 
 */
package codemining.lm.tsg;

import java.io.Serializable;

import codemining.ast.TreeNode;

/**
 * A posterior probability TSG computer.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public interface ITsgPosteriorProbabilityComputer<T extends Serializable>
		extends Serializable {

	/**
	 * Compute the posterior probability of a TSG.
	 * 
	 * @param tree
	 * @param remove TODO
	 * @return
	 */
	double computeLog2PosteriorProbabilityOfRule(final TreeNode<T> tree, boolean remove);

}

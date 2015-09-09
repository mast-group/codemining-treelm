/**
 * 
 */
package codemining.ast;

import java.io.Serializable;

/**
 * An interface for quantifying the distance among two trees.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public interface TreeDistance<T extends Serializable> extends Serializable {

	double distance(TreeNode<T> tree1, TreeNode<T> tree2);

}

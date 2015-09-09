/**
 * 
 */
package codemining.lm.tsg;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.google.common.base.Predicate;
import com.google.common.math.DoubleMath;

import codemining.ast.TreeNode;
import codemining.ast.TreeNode.NodeDataPair;
import codemining.lm.tsg.ITsgPosteriorProbabilityComputer;
import codemining.lm.tsg.TSGrammar;
import codemining.lm.tsg.TreeProbabilityComputer;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class TreeProbabilityComputerTest {

	public static final class MlPosteriorComputer implements
			ITsgPosteriorProbabilityComputer<String> {
		private final TSGrammar<String> grammar;

		public MlPosteriorComputer(final TSGrammar<String> grammar) {
			this.grammar = grammar;
		}

		@Override
		public double computeLog2PosteriorProbabilityOfRule(
				final TreeNode<String> tree, final boolean remove) {
			double nRulesCommonRoot = grammar
					.countTreesWithRoot(tree.getData());
			double nRulesInGrammar = grammar.countTreeOccurences(tree);

			if (nRulesInGrammar > 0 && remove) {
				nRulesInGrammar--;
				nRulesCommonRoot--;
			}

			return DoubleMath.log2(nRulesInGrammar / nRulesCommonRoot);
		}
	}

	/**
	 * The default matching predicate.
	 */
	public static final Predicate<NodeDataPair<String>> DEFAULT_MATCHER = new Predicate<NodeDataPair<String>>() {

		@Override
		public boolean apply(final NodeDataPair<String> pair) {
			return pair.fromNode.equals(pair.toNode);
		}

	};

	private TreeNode<String> generateRule1() {
		final TreeNode<String> a = TreeNode.create("A", 1);

		final TreeNode<String> b = TreeNode.create("B", 1);
		final TreeNode<String> c = TreeNode.create("C", 1);
		a.addChildNode(b, 0);
		a.addChildNode(c, 0);
		return a;
	}

	private TreeNode<String> generateRule2() {
		final TreeNode<String> b = TreeNode.create("B", 1);

		final TreeNode<String> d = TreeNode.create("D", 1);
		final TreeNode<String> e = TreeNode.create("E", 1);
		b.addChildNode(d, 0);
		b.addChildNode(e, 0);
		return b;
	}

	private TreeNode<String> generateRule3() {
		final TreeNode<String> b = TreeNode.create("B", 1);

		final TreeNode<String> d = TreeNode.create("D", 1);
		final TreeNode<String> f = TreeNode.create("F", 1);
		b.addChildNode(d, 0);
		b.addChildNode(f, 0);
		return b;
	}

	private TreeNode<String> generateRule4() {
		final TreeNode<String> b = TreeNode.create("D", 1);

		final TreeNode<String> d = TreeNode.create("A", 1);
		final TreeNode<String> f = TreeNode.create("C", 1);
		b.addChildNode(d, 0);
		b.addChildNode(f, 0);
		return b;
	}

	private TreeNode<String> generateTree() {
		final TreeNode<String> a = TreeNode.create("A", 1);

		final TreeNode<String> b = TreeNode.create("B", 1);
		final TreeNode<String> c = TreeNode.create("C", 1);
		a.addChildNode(b, 0);
		a.addChildNode(c, 0);

		final TreeNode<String> d = TreeNode.create("D", 1);
		final TreeNode<String> e = TreeNode.create("E", 1);

		b.addChildNode(d, 0);
		b.addChildNode(e, 0);

		return a;
	}

	@Test
	public void testAutocompletion() {
		final TSGrammar<String> grammar = new TSGrammar<String>();

		final ITsgPosteriorProbabilityComputer<String> mlPosteriorComputer = new MlPosteriorComputer(
				grammar);
		grammar.setPosteriorComputer(mlPosteriorComputer);

		grammar.addTree(generateTree());
		grammar.addTree(generateRule1());

		grammar.addTree(generateRule2());
		grammar.addTree(generateRule3());

		final TreeProbabilityComputer<String> computer = new TreeProbabilityComputer<String>(
				grammar, false, DEFAULT_MATCHER);

		assertEquals(computer.getLog2ProbabilityOf(generateTree()),
				DoubleMath.log2(3. / 4.), 10E-10);
	}

	@Test
	public void testFull() {
		final TSGrammar<String> grammar = new TSGrammar<String>();

		final ITsgPosteriorProbabilityComputer<String> mlPosteriorComputer = new MlPosteriorComputer(
				grammar);

		grammar.setPosteriorComputer(mlPosteriorComputer);

		grammar.addTree(generateTree());
		grammar.addTree(generateRule1());

		grammar.addTree(generateRule2());
		grammar.addTree(generateRule3());

		final TreeProbabilityComputer<String> computer = new TreeProbabilityComputer<String>(
				grammar, true, DEFAULT_MATCHER);

		assertEquals(computer.getLog2ProbabilityOf(generateTree()),
				DoubleMath.log2(3. / 4.), 10E-10);

		final TreeProbabilityComputer<String> computer2 = new TreeProbabilityComputer<String>(
				grammar, true, DEFAULT_MATCHER);

		final TreeNode<String> fullTree = generateTree();
		fullTree.getChild(0, 0).getChild(0, 0)
				.addChildNode(TreeNode.create("D", 1), 0);

		grammar.addTree(generateRule4());
		assertEquals(computer2.getLog2ProbabilityOf(fullTree),
				Double.NEGATIVE_INFINITY, 10E-10);

	}
}

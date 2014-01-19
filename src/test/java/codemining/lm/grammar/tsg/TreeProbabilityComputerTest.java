/**
 * 
 */
package codemining.lm.grammar.tsg;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tree.TreeNode.NodeDataPair;

import com.google.common.base.Predicate;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.math.DoubleMath;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class TreeProbabilityComputerTest {

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
		final Map<String, Multiset<TreeNode<String>>> grammar = Maps
				.newTreeMap();
		final Multiset<TreeNode<String>> aRules = HashMultiset.create();

		aRules.add(generateTree(), 1);
		aRules.add(generateRule1(), 1);
		grammar.put("A", aRules);

		final Multiset<TreeNode<String>> bRules = HashMultiset.create();
		bRules.add(generateRule2(), 1);
		bRules.add(generateRule3(), 1);
		grammar.put("B", bRules);

		final TreeProbabilityComputer<String> computer = new TreeProbabilityComputer<String>(
				grammar, false, DEFAULT_MATCHER);

		assertEquals(computer.getLog2ProbabilityOf(generateTree()),
				DoubleMath.log2(3. / 4.), 10E-10);

	}

	@Test
	public void testFull() {
		final Map<String, Multiset<TreeNode<String>>> grammar = Maps
				.newTreeMap();
		final Multiset<TreeNode<String>> aRules = HashMultiset.create();

		aRules.add(generateTree(), 1);
		aRules.add(generateRule1(), 1);
		grammar.put("A", aRules);

		final Multiset<TreeNode<String>> bRules = HashMultiset.create();
		bRules.add(generateRule2(), 1);
		bRules.add(generateRule3(), 1);
		grammar.put("B", bRules);

		final TreeProbabilityComputer<String> computer = new TreeProbabilityComputer<String>(
				grammar, true, DEFAULT_MATCHER);

		assertEquals(computer.getLog2ProbabilityOf(generateTree()),
				DoubleMath.log2(3. / 4.), 10E-10);

		final TreeProbabilityComputer<String> computer2 = new TreeProbabilityComputer<String>(
				grammar, true, DEFAULT_MATCHER);

		final TreeNode<String> fullTree = generateTree();
		fullTree.getChild(0, 0).getChild(0, 0)
				.addChildNode(TreeNode.create("D", 1), 0);

		final HashMultiset<TreeNode<String>> dRules = HashMultiset.create();
		dRules.add(generateRule4());
		grammar.put("D", dRules);
		assertEquals(computer2.getLog2ProbabilityOf(fullTree),
				Double.NEGATIVE_INFINITY, 10E-10);

	}
}

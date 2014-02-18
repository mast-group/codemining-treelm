package codemining.lm.grammar.cfg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

import codemining.lm.grammar.cfg.AbstractContextFreeGrammar.CFGRule;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.ITreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

public class ContextFreeGrammarTest {

	private TreeNode<Integer> generateSampleTree1() {
		final TreeNode<Integer> root = TreeNode.create(1, 3);

		final TreeNode<Integer> child1 = TreeNode.create(2, 1);
		final TreeNode<Integer> child2 = TreeNode.create(3, 2);

		root.addChildNode(child1, 2);
		root.addChildNode(child2, 1);

		final TreeNode<Integer> grandchild1 = TreeNode.create(4, 0);
		final TreeNode<Integer> grandchild2 = TreeNode.create(5, 0);
		final TreeNode<Integer> grandchild3 = TreeNode.create(6, 0);

		child1.addChildNode(grandchild1, 0);
		child2.addChildNode(grandchild2, 1);
		child2.addChildNode(grandchild3, 0);

		return root;
	}

	private TreeNode<Integer> generateSampleTree2() {
		final TreeNode<Integer> root = TreeNode.create(1, 3);

		final TreeNode<Integer> child1 = TreeNode.create(2, 1);
		final TreeNode<Integer> child2 = TreeNode.create(4, 0);

		root.addChildNode(child1, 2);
		root.addChildNode(child2, 1);

		final TreeNode<Integer> grandchild1 = TreeNode.create(3, 2);
		final TreeNode<Integer> grandchild2 = TreeNode.create(5, 0);

		child1.addChildNode(grandchild1, 0);
		child1.addChildNode(grandchild2, 0);

		return root;
	}

	/**
	 * @return
	 */
	private ContextFreeGrammar.NodeConsequent getConsequent1() {
		final ContextFreeGrammar.NodeConsequent csq = new ContextFreeGrammar.NodeConsequent();
		csq.nodes.add(new ArrayList<Integer>());
		csq.nodes.add(new ArrayList<Integer>());
		csq.nodes.add(new ArrayList<Integer>());
		csq.nodes.get(1).add(3);
		csq.nodes.get(2).add(2);
		return csq;
	}

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testCFGRules() {
		final AbstractContextFreeGrammar cfg = new ContextFreeGrammar(
				mock(AbstractJavaTreeExtractor.class));
		testCFGRules(cfg);
		cfg.addRulesFrom(generateSampleTree2());
		assertEquals(cfg.getMLProbability(1, getConsequent1()), .5, 10E-10);

		cfg.addRulesFrom(generateSampleTree2());
		assertEquals(cfg.getMLProbability(1, getConsequent1()), 1. / 3., 10E-10);
	}

	/**
	 * @param cfg
	 */
	public void testCFGRules(final AbstractContextFreeGrammar cfg) {
		cfg.addRulesFrom(generateSampleTree1());
		assertEquals(cfg.getMLProbability(1, getConsequent1()), 1, 10E-10);

		final ContextFreeGrammar.NodeConsequent csq2 = new ContextFreeGrammar.NodeConsequent();
		csq2.nodes.add(new ArrayList<Integer>());
		csq2.nodes.get(0).add(4);
		assertEquals(cfg.getMLProbability(2, csq2), 1, 10E-10);

		final ContextFreeGrammar.NodeConsequent csq3 = new ContextFreeGrammar.NodeConsequent();
		csq3.nodes.add(new ArrayList<Integer>());
		csq3.nodes.add(new ArrayList<Integer>());
		csq3.nodes.get(1).add(5);
		csq3.nodes.get(0).add(6);
		assertEquals(cfg.getMLProbability(3, csq2), 0, 10E-10);
		assertEquals(cfg.getMLProbability(3, csq3), 1, 10E-10);

	}

	@Test
	public void testRuleExtraction() {
		final ContextFreeGrammar cfg = new ContextFreeGrammar(
				mock(ITreeExtractor.class));
		final CFGRule rule = cfg.createCFRuleForNode(generateSampleTree1());
		cfg.addCFGRule(rule);
		assertFalse(cfg.grammar.isEmpty());
		assertTrue(cfg.grammar.containsKey(1));
		assertEquals(cfg.grammar.get(1).size(), 1);

		final ContextFreeGrammar.NodeConsequent csq = getConsequent1();

		assertTrue(cfg.grammar.get(1).contains(csq));
	}

	@Test
	public void testSerialization() throws SerializationException {
		final AbstractContextFreeGrammar cfg = new ContextFreeGrammar(
				mock(AbstractJavaTreeExtractor.class));
		testCFGRules(cfg);

		final byte[] serialized = Serializer.getSerializer().serialize(cfg);
		final AbstractContextFreeGrammar cfg2 = (AbstractContextFreeGrammar) Serializer
				.getSerializer().deserializeFrom(serialized);

		testCFGRules(cfg2);
	}

}

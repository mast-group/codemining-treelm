package codemining.lm.grammar.tsg.samplers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;

import org.junit.Test;

import codemining.lm.grammar.cfg.AbstractContextFreeGrammar.NodeConsequent;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.samplers.AbstractCollapsedGibbsSampler;
import codemining.lm.grammar.tsg.samplers.CollapsedGibbsSampler;
import codemining.lm.grammar.tsg.samplers.CollapsedGibbsSampler.CFGRule;

import com.google.common.math.DoubleMath;

public class CollapsedGibbsSamplerTest {

	public TreeNode<TSGNode> generateSampleTree() {
		final TSGNode rootNode = new TSGNode(1);
		rootNode.isRoot = true;
		final TreeNode<TSGNode> root = TreeNode.create(rootNode, 2);

		final TSGNode child1Node = new TSGNode(2);
		final TreeNode<TSGNode> child1 = TreeNode.create(child1Node, 2);
		final TSGNode child2Node = new TSGNode(2);
		final TreeNode<TSGNode> child2 = TreeNode.create(child2Node, 1);

		root.addChildNode(child1, 0);
		root.addChildNode(child2, 1);

		final TSGNode grandchild1Node = new TSGNode(4);
		final TreeNode<TSGNode> grandchild1 = TreeNode.create(grandchild1Node,
				0);
		final TSGNode grandchild2Node = new TSGNode(5);
		final TreeNode<TSGNode> grandchild2 = TreeNode.create(grandchild2Node,
				0);
		final TSGNode grandchild3Node = new TSGNode(6);
		final TreeNode<TSGNode> grandchild3 = TreeNode.create(grandchild3Node,
				0);

		child1.addChildNode(grandchild1, 0);
		child2.addChildNode(grandchild2, 0);
		child2.addChildNode(grandchild3, 0);

		return root;
	}

	@Test
	public void getPosteriorForTree() {
		final JavaFormattedTSGrammar mockGrammar = new JavaFormattedTSGrammar(
				mock(AbstractJavaTreeExtractor.class));
		final CollapsedGibbsSampler sampler = new CollapsedGibbsSampler(10, 10,
				mockGrammar, mockGrammar);
		sampler.addTree(generateSampleTree());

		final double geometricProb = Math.pow(.9, 5) * .1;
		final double prior = .25 * geometricProb;
		assertEquals(sampler.getPosteriorLog2ProbabilityForTree(
				generateSampleTree(), true), DoubleMath.log2(prior), 10E-10);

		assertEquals(sampler.getPosteriorLog2ProbabilityForTree(
				generateSampleTree(), false),
				DoubleMath.log2((1. + 10 * prior) / 11), 10E-10);

		sampler.lockSamplerData();
		assertEquals(sampler.getPosteriorLog2ProbabilityForTree(
				generateSampleTree(), true), DoubleMath.log2(prior), 10E-10);

		assertEquals(sampler.getPosteriorLog2ProbabilityForTree(
				generateSampleTree(), false),
				DoubleMath.log2((1. + 10 * prior) / 11), 10E-10);
	}

	@Test
	public void testGetPosteriorProbabilityForTree() {
		final JavaFormattedTSGrammar mockGrammar = new JavaFormattedTSGrammar(
				mock(AbstractJavaTreeExtractor.class));
		final CollapsedGibbsSampler sampler = new CollapsedGibbsSampler(5, 10,
				mockGrammar, mockGrammar);
		sampler.addTree(generateSampleTree());

		final NodeConsequent nc = new NodeConsequent();
		nc.nodes.add(new ArrayList<Integer>());
		nc.nodes.add(new ArrayList<Integer>());
		nc.nodes.get(0).add(2);
		nc.nodes.get(1).add(2);

		final NodeConsequent nc2 = new NodeConsequent();
		nc2.nodes.add(new ArrayList<Integer>());
		nc2.nodes.get(0).add(5);
		nc2.nodes.get(0).add(6);

		assertEquals(sampler.getLog2ProbForCFG(new CFGRule(1, nc)), 0, 0);
		assertEquals(sampler.getLog2ProbForCFG(new CFGRule(2, nc2)), -1, 0);

		sampler.lockSamplerData();
		assertEquals(sampler.getLog2ProbForCFG(new CFGRule(1, nc)), 0, 0);
		assertEquals(sampler.getLog2ProbForCFG(new CFGRule(2, nc2)), -1, 0);
	}

	@Test
	public void testPriorForTree() {
		final JavaFormattedTSGrammar mockGrammar = new JavaFormattedTSGrammar(
				mock(AbstractJavaTreeExtractor.class));
		final CollapsedGibbsSampler sampler = new CollapsedGibbsSampler(10, 10,
				mockGrammar, mockGrammar);
		sampler.addTree(generateSampleTree());

		final double geometricProb = Math.pow(.9, 5) * .1;
		assertEquals(sampler.getLog2PriorForTree(generateSampleTree()),
				DoubleMath.log2(.25 * geometricProb), 10E-10);

		sampler.lockSamplerData();
		assertEquals(sampler.getLog2PriorForTree(generateSampleTree()),
				DoubleMath.log2(.25 * geometricProb), 10E-10);
	}

	@Test
	public void testSample() {
		final JavaFormattedTSGrammar mockGrammar = new JavaFormattedTSGrammar(
				mock(AbstractJavaTreeExtractor.class));
		final TreeNode<TSGNode> root = generateSampleTree();
		final TreeNode<TSGNode> toBeSampled = root.getChild(0, 1);

		final AbstractCollapsedGibbsSampler sampler = new CollapsedGibbsSampler(
				10, 10, mockGrammar, mockGrammar);
		sampler.addTree(generateSampleTree());

		testSampler(root, toBeSampled, sampler);
	}

	/**
	 * @param root
	 * @param toBeSampled
	 * @param sampler
	 */
	public void testSampler(final TreeNode<TSGNode> root,
			final TreeNode<TSGNode> toBeSampled,
			final AbstractCollapsedGibbsSampler sampler) {
		int countRoot = 0;
		final double geometricProb = Math.pow(.9, 5) * .1;
		final double prior = .25 * geometricProb;

		for (int i = 0; i < 10000; i++) {
			sampler.sampleAt(toBeSampled, root);
			if (toBeSampled.getData().isRoot) {
				countRoot++;
				assertEquals(sampler.getPosteriorLog2ProbabilityForTree(
						generateSampleTree(), true),
						DoubleMath.log2(prior * 10. / 11), 10E-10);
				assertEquals(sampler.getPosteriorLog2ProbabilityForTree(
						generateSampleTree(), false),
						DoubleMath.log2((10 * prior) / 11), 10E-10);

			} else {
				assertEquals(sampler.getPosteriorLog2ProbabilityForTree(
						generateSampleTree(), true), DoubleMath.log2(prior),
						10E-10);
				assertEquals(sampler.getPosteriorLog2ProbabilityForTree(
						generateSampleTree(), false),
						DoubleMath.log2((1. + 10 * prior) / 11), 10E-10);
			}
			assertEquals(sampler.getTreeCorpus().size(), 1);
		}

		assertEquals(((double) countRoot) / 10000, .09, .1);
	}

	@Test
	public void testSampleWithLock() {
		final JavaFormattedTSGrammar mockGrammar = new JavaFormattedTSGrammar(
				mock(AbstractJavaTreeExtractor.class));
		final TreeNode<TSGNode> root = generateSampleTree();
		final TreeNode<TSGNode> toBeSampled = root.getChild(0, 1);

		final CollapsedGibbsSampler sampler = new CollapsedGibbsSampler(10, 10,
				mockGrammar, mockGrammar);
		sampler.addTree(generateSampleTree());
		sampler.lockSamplerData();
		testSampler(root, toBeSampled, sampler);
	}
}

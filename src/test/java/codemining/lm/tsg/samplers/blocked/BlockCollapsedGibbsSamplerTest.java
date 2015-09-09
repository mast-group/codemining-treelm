package codemining.lm.tsg.samplers.blocked;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;

import org.junit.Test;

import com.google.common.math.DoubleMath;

import codemining.ast.TreeNode;
import codemining.ast.java.AbstractJavaTreeExtractor;
import codemining.lm.cfg.AbstractContextFreeGrammar;
import codemining.lm.cfg.AbstractContextFreeGrammar.NodeConsequent;
import codemining.lm.tsg.FormattedTSGrammar;
import codemining.lm.tsg.TSGNode;
import codemining.lm.tsg.samplers.blocked.BlockCollapsedGibbsSampler;

public class BlockCollapsedGibbsSamplerTest {

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
        final TreeNode<TSGNode> grandchild1 = TreeNode.create(grandchild1Node, 0);
        final TSGNode grandchild2Node = new TSGNode(5);
        final TreeNode<TSGNode> grandchild2 = TreeNode.create(grandchild2Node, 0);
        final TSGNode grandchild3Node = new TSGNode(6);
        final TreeNode<TSGNode> grandchild3 = TreeNode.create(grandchild3Node, 0);

        child1.addChildNode(grandchild1, 0);
        child2.addChildNode(grandchild2, 0);
        child2.addChildNode(grandchild3, 0);

        return root;
    }

    @Test
    public void getPosteriorForTree() {
        final FormattedTSGrammar mockGrammar = new FormattedTSGrammar(mock(AbstractJavaTreeExtractor.class));
        final BlockCollapsedGibbsSampler sampler = new BlockCollapsedGibbsSampler(10, 10, mockGrammar, mockGrammar);
        sampler.addTree(generateSampleTree(), true);

        final double geometricProb = 1; // Math.pow(.9, 5) * .1;
        final double prior = .25 * geometricProb;
        assertEquals(sampler.getSampleGrammar().computeRulePosteriorLog2Probability(generateSampleTree(), true),
                DoubleMath.log2(prior), 10E-10);

        assertEquals(sampler.getSampleGrammar().computeRulePosteriorLog2Probability(generateSampleTree(), false),
                DoubleMath.log2((1. + 10 * prior) / 11), 10E-10);

        sampler.lockSamplerData();
        assertEquals(sampler.getSampleGrammar().computeRulePosteriorLog2Probability(generateSampleTree(), true),
                DoubleMath.log2(prior), 10E-10);

        assertEquals(sampler.getSampleGrammar().computeRulePosteriorLog2Probability(generateSampleTree(), false),
                DoubleMath.log2((1. + 10 * prior) / 11), 10E-10);
    }

    @Test
    public void testGetPosteriorProbabilityForTree() {
        final FormattedTSGrammar mockGrammar = new FormattedTSGrammar(mock(AbstractJavaTreeExtractor.class));
        final BlockCollapsedGibbsSampler sampler = new BlockCollapsedGibbsSampler(5, 10, mockGrammar, mockGrammar);
        sampler.addTree(generateSampleTree(), true);

        final NodeConsequent nc = new NodeConsequent();
        nc.nodes.add(new ArrayList<Integer>());
        nc.nodes.add(new ArrayList<Integer>());
        nc.nodes.get(0).add(2);
        nc.nodes.get(1).add(2);

        final NodeConsequent nc2 = new NodeConsequent();
        nc2.nodes.add(new ArrayList<Integer>());
        nc2.nodes.get(0).add(5);
        nc2.nodes.get(0).add(6);

        assertEquals(sampler.getPosteriorComputer().getPrior()
                .getLog2ProbForCFG(new AbstractContextFreeGrammar.CFGRule(1, nc)), 0, 0);
        assertEquals(sampler.getPosteriorComputer().getPrior()
                .getLog2ProbForCFG(new AbstractContextFreeGrammar.CFGRule(2, nc2)), -1, 0);

        sampler.lockSamplerData();
        assertEquals(sampler.getPosteriorComputer().getPrior()
                .getLog2ProbForCFG(new AbstractContextFreeGrammar.CFGRule(1, nc)), 0, 0);
        assertEquals(sampler.getPosteriorComputer().getPrior()
                .getLog2ProbForCFG(new AbstractContextFreeGrammar.CFGRule(2, nc2)), -1, 0);
    }

    @Test
    public void testPriorForTree() {
        final FormattedTSGrammar mockGrammar = new FormattedTSGrammar(mock(AbstractJavaTreeExtractor.class));
        final BlockCollapsedGibbsSampler sampler = new BlockCollapsedGibbsSampler(10, 10, mockGrammar, mockGrammar);
        sampler.addTree(generateSampleTree(), true);

        final double geometricProb = 1; // Math.pow(.9, 5) * .1;
        assertEquals(sampler.getPosteriorComputer().getLog2PriorForTree(generateSampleTree()),
                DoubleMath.log2(.25 * geometricProb), 10E-10);

        sampler.lockSamplerData();
        assertEquals(sampler.getPosteriorComputer().getLog2PriorForTree(generateSampleTree()),
                DoubleMath.log2(.25 * geometricProb), 10E-10);
    }

    @Test
    public void testSample() {
        final FormattedTSGrammar mockGrammar = new FormattedTSGrammar(mock(AbstractJavaTreeExtractor.class));
        final TreeNode<TSGNode> root = generateSampleTree();

        final BlockCollapsedGibbsSampler sampler = new BlockCollapsedGibbsSampler(10, 10, mockGrammar, mockGrammar);
        final TreeNode<TSGNode> addedTree = sampler.addTree(root, true);
        final TreeNode<TSGNode> toBeSampled = addedTree.getChild(0, 1);

        testSampler(addedTree, toBeSampled, sampler);
    }

    /**
     * @param root
     * @param toBeSampled
     * @param sampler
     */
    public void testSampler(final TreeNode<TSGNode> root, final TreeNode<TSGNode> toBeSampled,
            final BlockCollapsedGibbsSampler sampler) {
        int countRoot = 0;
        final double geometricProb = 1; // Math.pow(.9, 5) * .1;
        final double prior = .25 * geometricProb;

        for (int i = 0; i < 10000; i++) {
            sampler.sampleAt(toBeSampled);
            if (toBeSampled.getData().isRoot) {
                countRoot++;
                assertEquals(sampler.getSampleGrammar().computeRulePosteriorLog2Probability(generateSampleTree(), true),
                        DoubleMath.log2(prior * 10. / 11), 10E-10);
                assertEquals(
                        sampler.getSampleGrammar().computeRulePosteriorLog2Probability(generateSampleTree(), false),
                        DoubleMath.log2((10 * prior) / 11), 10E-10);

            } else {
                assertEquals(sampler.getSampleGrammar().computeRulePosteriorLog2Probability(generateSampleTree(), true),
                        DoubleMath.log2(prior), 10E-10);
                assertEquals(
                        sampler.getSampleGrammar().computeRulePosteriorLog2Probability(generateSampleTree(), false),
                        DoubleMath.log2((1. + 10 * prior) / 11), 10E-10);
            }
            assertEquals(sampler.getTreeCorpus().size(), 1);
        }

        assertEquals(((double) countRoot) / 10000, .5, .1);
    }

    @Test
    public void testSampleWithLock() {
        final FormattedTSGrammar mockGrammar = new FormattedTSGrammar(mock(AbstractJavaTreeExtractor.class));
        final TreeNode<TSGNode> root = generateSampleTree();

        final BlockCollapsedGibbsSampler sampler = new BlockCollapsedGibbsSampler(10, 10, mockGrammar, mockGrammar);
        final TreeNode<TSGNode> addedTree = sampler.addTree(generateSampleTree(), true);
        sampler.lockSamplerData();

        final TreeNode<TSGNode> toBeSampled = addedTree.getChild(0, 1);
        testSampler(addedTree, toBeSampled, sampler);
    }

}

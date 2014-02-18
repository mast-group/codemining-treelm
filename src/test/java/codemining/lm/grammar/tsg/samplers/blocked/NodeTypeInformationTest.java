package codemining.lm.grammar.tsg.samplers.blocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.samplers.blocked.NodeTypeInformation.NodeType;

import com.google.common.collect.Lists;

public class NodeTypeInformationTest {

	/**
	 * @param nti
	 * @param tree1
	 * @param tree2
	 */
	protected void checkTypeEqualities(final NodeTypeInformation nti,
			final TreeWithNodeIndex tree1, final TreeWithNodeIndex tree2) {
		// Check equalities
		for (int i = 1; i <= 12; i++) {
			final TreeNode<TSGNode> node1 = tree1.nodeIndex.get(i);
			final TreeNode<TSGNode> parent1 = nti.getParentOf(node1);
			final NodeType node1type = new NodeType(node1, parent1);
			assertEquals(node1type, node1type);

			final TreeNode<TSGNode> node2 = tree2.nodeIndex.get(i);
			final TreeNode<TSGNode> parent2 = nti.getParentOf(node2);

			assertEquals(node1.getData().nodeKey, node2.getData().nodeKey);
			if (parent1 != null) {
				assertEquals(parent1.getData().nodeKey,
						parent2.getData().nodeKey);
			} else {
				assertEquals(parent1, parent2);
			}

			final NodeType node2type = new NodeType(node2, parent2);
			assertEquals(node1type, node2type);
			assertEquals(node1type.hashCode(), node2type.hashCode());
		}

		// Check for inequality
		for (int i = 1; i <= 11; i++) {
			final TreeNode<TSGNode> node1 = tree1.nodeIndex.get(i);
			final TreeNode<TSGNode> parent1 = nti.getParentOf(node1);

			final TreeNode<TSGNode> node2 = tree2.nodeIndex.get(i + 1);
			final TreeNode<TSGNode> parent2 = nti.getParentOf(node2);

			assertFalse(node1.equals(node2));

			final NodeType node1type = new NodeType(node1, parent1);
			final NodeType node2type = new NodeType(node2, parent2);
			assertFalse(node1type + " should not be equal with " + node2type,
					node1type.equals(node2type));
		}
	}

	@Test
	public void testGetInternalRules() {
		final TreeWithNodeIndex tree1 = TreeWithNodeIndex.generateTree1();
		final Set<TreeNode<TSGNode>> internalNodes = NodeTypeInformation
				.getNodesInSelf(tree1.nodeIndex.get(4), tree1.nodeIndex.get(1));
		assertEquals(internalNodes.size(), 11);
		for (int i = 1; i <= 12; i++) {
			if (i != 4) {
				assertTrue(i + " is not contained",
						internalNodes.contains(tree1.nodeIndex.get(i)));
			}
		}
		assertFalse(internalNodes.contains(tree1.nodeIndex.get(4)));

		tree1.nodeIndex.get(2).getData().isRoot = true;
		tree1.nodeIndex.get(6).getData().isRoot = true;

		final Set<TreeNode<TSGNode>> internalNodes2 = NodeTypeInformation
				.getNodesInSelf(tree1.nodeIndex.get(4), tree1.nodeIndex.get(2));
		assertEquals(internalNodes2.size(), 8);
		assertFalse(internalNodes2.contains(tree1.nodeIndex.get(1)));
		assertTrue(internalNodes2.contains(tree1.nodeIndex.get(2)));
		assertFalse(internalNodes2.contains(tree1.nodeIndex.get(3)));
		assertFalse(internalNodes2.contains(tree1.nodeIndex.get(4)));
		assertTrue(internalNodes2.contains(tree1.nodeIndex.get(5)));
		assertTrue(internalNodes2.contains(tree1.nodeIndex.get(6)));
		assertTrue(internalNodes2.contains(tree1.nodeIndex.get(7)));
		assertTrue(internalNodes2.contains(tree1.nodeIndex.get(8)));
		assertFalse(internalNodes2.contains(tree1.nodeIndex.get(9)));
		assertTrue(internalNodes2.contains(tree1.nodeIndex.get(10)));
		assertTrue(internalNodes2.contains(tree1.nodeIndex.get(11)));
		assertTrue(internalNodes2.contains(tree1.nodeIndex.get(12)));

	}

	@Test
	public void testGetRoot() {
		final NodeTypeInformation nti = new NodeTypeInformation();
		final TreeWithNodeIndex tree1 = TreeWithNodeIndex.generateTree1();
		nti.updateCorpusStructures(tree1.tree);

		assertTrue(nti.getRootForNode(tree1.nodeIndex.get(1)) == null);

		for (int i = 2; i <= 12; i++) {
			assertTrue(nti.getRootForNode(tree1.nodeIndex.get(i)) == tree1.nodeIndex
					.get(1));
		}

	}

	@Test
	public void testNodeType() {
		final NodeTypeInformation nti = new NodeTypeInformation();
		final TreeWithNodeIndex tree1 = TreeWithNodeIndex.generateTree1();
		final TreeWithNodeIndex tree2 = TreeWithNodeIndex.generateTree1();
		nti.updateCorpusStructures(tree1.tree);
		nti.updateCorpusStructures(tree2.tree);

		// Gradually set everything as root and check that types are not
		// altered
		for (int i = 1; i <= 12; i++) {
			tree1.nodeIndex.get(i).getData().isRoot = true;
			checkTypeEqualities(nti, tree1, tree2);
		}

	}

	@Test
	public void testNodeType2() {
		final NodeTypeInformation nti = new NodeTypeInformation();
		final TreeWithNodeIndex tree1 = TreeWithNodeIndex.generateTree1();
		nti.updateCorpusStructures(tree1.tree);
		final TreeWithNodeIndex tree2 = TreeWithNodeIndex.generateTree2();
		nti.updateCorpusStructures(tree2.tree);

		// With no roots no same type nodes should be found
		for (int i = 2; i <= 12; i++) {
			final TreeNode<TSGNode> sampleNode = tree1.nodeIndex.get(i);

			final Collection<TreeNode<TSGNode>> sameTypeNodes = nti
					.getSameTypeNodes(sampleNode);
			assertEquals(sameTypeNodes.size(), 1);
			assertTrue(sameTypeNodes.contains(sampleNode));
		}

		for (int i = 2; i <= 17; i++) {
			final TreeNode<TSGNode> sampleNode = tree2.nodeIndex.get(i);

			final Collection<TreeNode<TSGNode>> sameTypeNodes = nti
					.getSameTypeNodes(sampleNode);
			assertEquals(sameTypeNodes.size(), 1);
			assertTrue(sameTypeNodes.contains(sampleNode));
		}

		// But..
		tree2.nodeIndex.get(4).getData().isRoot = true;
		tree2.nodeIndex.get(13).getData().isRoot = true;

		List<Integer> eqNodes = Lists.newArrayList(6, 7, 9, 10, 14, 15, 16, 17);
		for (int i = 2; i <= 17; i++) {
			final TreeNode<TSGNode> sampleNode = tree2.nodeIndex.get(i);

			final Collection<TreeNode<TSGNode>> sameTypeNodes = nti
					.getSameTypeNodes(sampleNode);
			if (eqNodes.contains(i)) {
				assertEquals(i + " should have a second same-type node",
						sameTypeNodes.size(), 2);
			} else {
				assertEquals(sameTypeNodes.size(), 1);
			}
			assertTrue(sameTypeNodes.contains(sampleNode));
		}

		tree1.nodeIndex.get(4).getData().isRoot = true;
		eqNodes = Lists.newArrayList(6, 7, 9, 10);
		for (int i = 2; i <= 12; i++) {
			final TreeNode<TSGNode> sampleNode = tree1.nodeIndex.get(i);

			final Collection<TreeNode<TSGNode>> sameTypeNodes = nti
					.getSameTypeNodes(sampleNode);
			if (eqNodes.contains(i)) {
				assertEquals(i + " should have a second same-type node",
						sameTypeNodes.size(), 3);
			} else {
				assertEquals(sameTypeNodes.size(), 1);
			}
			assertTrue(sameTypeNodes.contains(sampleNode));
		}
	}

	@Test
	public void testSameType() {
		final NodeTypeInformation nti = new NodeTypeInformation();
		final TreeWithNodeIndex tree1 = TreeWithNodeIndex.generateTree1();
		nti.updateCorpusStructures(tree1.tree);
		for (int i = 0; i < 10; i++) {
			nti.updateCorpusStructures(TreeWithNodeIndex.generateTree1().tree);
		}

		// For all nodes, except the root
		for (int i = 2; i <= 12; i++) {
			final TreeNode<TSGNode> sampleNode = tree1.nodeIndex.get(i);

			final Collection<TreeNode<TSGNode>> sameTypeNodes = nti
					.getSameTypeNodes(sampleNode);
			assertEquals(sameTypeNodes.size(), 11);
			assertTrue(sameTypeNodes.contains(sampleNode));
		}

		// Now add a root, we should find just one node, except from when we
		// are sampling that node.
		for (int j = 2; j <= 12; j++) {
			tree1.nodeIndex.get(j).getData().isRoot = true;
			for (int i = 2; i <= 12; i++) {
				final TreeNode<TSGNode> sampleNode = tree1.nodeIndex.get(i);

				final Collection<TreeNode<TSGNode>> sameTypeNodes = nti
						.getSameTypeNodes(sampleNode);
				if (i == j) {
					assertEquals(sameTypeNodes.size(), 11);
				} else {
					assertEquals(sameTypeNodes.size(), 1);
				}
				assertTrue(sameTypeNodes.contains(sampleNode));
			}
			tree1.nodeIndex.get(j).getData().isRoot = false;
		}
	}
}

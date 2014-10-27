/**
 *
 */
package codemining.lm.grammar.tsg.samplers.blocked;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;

import codemining.lm.grammar.cfg.AbstractContextFreeGrammar.CFGRule;
import codemining.lm.grammar.cfg.AbstractContextFreeGrammar.NodeConsequent;
import codemining.lm.grammar.tree.AbstractTreeExtractor;
import codemining.lm.grammar.tree.AstNodeSymbol;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.util.SettingsLoader;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;

/**
 * A tree corpus filter, that filters rare nodes given a corpus of trees. This
 * class modifies the original trees
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public final class TreeCorpusFilter {

	private final Multiset<CFGRule> cfgCount = HashMultiset.create();

	private final List<TreeNode<TSGNode>> trees = Lists.newArrayList();

	private final int countLimit;

	private final AbstractTreeExtractor treeExtractor;

	public static final boolean REMOVE_VARIABLE_NAMES = SettingsLoader
			.getBooleanSetting("removeVariableNames", false);

	public TreeCorpusFilter(final AbstractTreeExtractor treeExtractor,
			final int countLimit) {
		this.countLimit = countLimit;
		this.treeExtractor = treeExtractor;
	}

	private void addAllNodes(final TreeNode<TSGNode> tree) {
		final ArrayDeque<TreeNode<TSGNode>> nodes = new ArrayDeque<TreeNode<TSGNode>>();
		nodes.push(tree);

		while (!nodes.isEmpty()) {
			final TreeNode<TSGNode> currentNode = nodes.pop();
			if (currentNode.isLeaf()) {
				continue;
			}

			final CFGRule rule = createRuleForNode(currentNode);
			cfgCount.add(rule);

			for (final List<TreeNode<TSGNode>> childProperty : currentNode
					.getChildrenByProperty()) {
				for (final TreeNode<TSGNode> child : childProperty) {
					nodes.push(child);
				}
			}

		}
	}

	public void addTree(final TreeNode<TSGNode> tree) {
		final TreeNode<TSGNode> currentTree;
		if (treeExtractor.getSymbol(tree.getData().nodeKey).nodeType == treeExtractor
				.getKeyForCompilationUnit().getData()) {
			// Ignore imports and package declaration
			if (tree.getChildrenByProperty().get(2).isEmpty()) {
				return;
			}
			currentTree = tree.getChild(0, 2);
			currentTree.getData().isRoot = true;
		} else {
			currentTree = tree;
		}

		trees.add(currentTree);
		addAllNodes(currentTree);
	}

	private CFGRule createRuleForNode(final TreeNode<TSGNode> node) {
		final int rootId = node.getData().nodeKey;

		final int nProperties = node.nProperties();
		final NodeConsequent ruleConsequent = new NodeConsequent(nProperties);
		for (int i = 0; i < nProperties; i++) {
			final List<TreeNode<TSGNode>> children = node
					.getChildrenByProperty().get(i);
			final int nChildren = children.size();
			ruleConsequent.nodes.add(Lists
					.<Integer> newArrayListWithCapacity(nChildren));
			for (int j = 0; j < nChildren; j++) {
				final int childNode = node.getChild(j, i).getData().nodeKey;
				ruleConsequent.nodes.get(i).add(childNode);
			}
		}

		return new CFGRule(rootId, ruleConsequent);
	}

	private Set<TreeNode<TSGNode>> filterTree(final TreeNode<TSGNode> tree) {
		final Set<TreeNode<TSGNode>> filteredRoots = Sets.newIdentityHashSet();
		filteredRoots.add(tree);
		final ArrayDeque<TreeNode<TSGNode>> nodes = new ArrayDeque<TreeNode<TSGNode>>();
		nodes.push(tree);

		while (!nodes.isEmpty()) {
			final TreeNode<TSGNode> currentNode = nodes.pop();

			if (currentNode.isLeaf()) {
				continue;
			} else if (REMOVE_VARIABLE_NAMES && isTemplateNode(currentNode)) {
				currentNode.getChildrenByProperty().clear();
				continue; // ignore all variables names
			}

			for (final List<TreeNode<TSGNode>> childProperty : currentNode
					.getChildrenByProperty()) {
				for (final TreeNode<TSGNode> child : childProperty) {
					nodes.push(child);
				}
			}

			final CFGRule rule = createRuleForNode(currentNode);
			if (cfgCount.count(rule) < countLimit) {
				// Remove relationship to children and add children as root
				for (final List<TreeNode<TSGNode>> childProperty : currentNode
						.getChildrenByProperty()) {
					filteredRoots.addAll(childProperty);
					childProperty.clear();
				}
			}
		}

		// Set everything as root
		filteredRoots.forEach(node -> node.getData().isRoot = true);

		// Remove all roots with no children nodes
		return Sets.filter(filteredRoots, input -> !input.isLeaf());
	}

	public List<TreeNode<TSGNode>> getFilteredTrees() {
		final List<TreeNode<TSGNode>> filteredTrees = Lists.newArrayList();
		for (final TreeNode<TSGNode> tree : trees) {
			filteredTrees.addAll(filterTree(tree));
		}

		return filteredTrees;
	}

	/**
	 * @param currentNode
	 * @return
	 */
	private boolean isTemplateNode(final TreeNode<TSGNode> currentNode) {
		return treeExtractor.getSymbol(currentNode.getData().nodeKey).nodeType == AstNodeSymbol.TEMPLATE_NODE;
	}

}

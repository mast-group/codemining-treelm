/**
 * 
 */
package codemining.lm.grammar.tsg;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang.NotImplementedException;

import codemining.lm.grammar.tree.ITreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.math.random.SampleUtils;
import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Multiset;
import com.google.common.collect.SortedMultiset;
import com.google.common.collect.TreeMultiset;

/**
 * A thread safe tree substitution grammar with nodes of data-type T.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class TSGrammar<T extends Serializable> implements
		ITreeSubstitutionGrammar<T> {

	private static final long serialVersionUID = 3178243087484789075L;

	/**
	 * A Map from a tree root to a multiset of productions.
	 */
	protected final ConcurrentMap<T, ConcurrentHashMultiset<TreeNode<T>>> grammar;

	/**
	 * UNK node.
	 */
	public final TreeNode<T> UNK_NODE;

	public TSGrammar() {
		grammar = (new MapMaker()).concurrencyLevel(
				ParallelThreadPool.NUM_THREADS).makeMap();
		UNK_NODE = TreeNode.create(null, 0);
	}

	/**
	 * Adds all tree production of other grammar to this grammar.
	 * 
	 * @param other
	 */
	public void addAll(final TSGrammar<T> other) {
		for (final ConcurrentHashMultiset<TreeNode<T>> treeSet : other.grammar
				.values()) {
			for (final com.google.common.collect.Multiset.Entry<TreeNode<T>> entry : treeSet
					.entrySet()) {
				addTree(entry.getElement(), entry.getCount());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tsg.ITreeSubstitutionGrammar#addTree(codemining
	 * .lm.grammar.tree.TreeNode)
	 */
	@Override
	public void addTree(final TreeNode<T> subTree) {
		addTree(subTree, 1);
	}

	/**
	 * Add a tree with the given number of times.
	 * 
	 * @param subTree
	 * @param count
	 */
	public void addTree(final TreeNode<T> subTree, final int count) {
		checkArgument(count > 0);
		final T rootNodeData = subTree.getData();

		final ConcurrentHashMultiset<TreeNode<T>> tempNew = ConcurrentHashMultiset
				.create();
		final ConcurrentHashMultiset<TreeNode<T>> nSet = grammar.putIfAbsent(
				rootNodeData, tempNew);

		if (nSet != null) {
			nSet.add(subTree, count);
		} else {
			tempNew.add(subTree, count);
		}
	}

	public void clear() {
		grammar.clear();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tsg.ITreeSubstitutionGrammar#computeTreeSizeStats()
	 */
	@Override
	public SortedMultiset<Integer> computeGrammarTreeSizeStats() {
		// Get tree size distribution.
		final SortedMultiset<Integer> treeSizes = TreeMultiset.create();
		for (final Entry<T, ConcurrentHashMultiset<TreeNode<T>>> entry : grammar
				.entrySet()) {
			for (final com.google.common.collect.Multiset.Entry<TreeNode<T>> rule : entry
					.getValue().entrySet()) {
				treeSizes.add(TreeNode.getTreeSize(rule.getElement()),
						rule.getCount());
			}
		}
		return treeSizes;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tsg.ITreeSubstitutionGrammar#countTreeOccurences
	 * (codemining.lm.grammar.tree.TreeNode)
	 */
	@Override
	public int countTreeOccurences(final TreeNode<T> root) {
		final ConcurrentHashMultiset<TreeNode<T>> set = grammar.get(root
				.getData());
		if (set == null) {
			return 0;
		}
		return set.count(root);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tsg.ITreeSubstitutionGrammar#countTreesWithRoot(T)
	 */
	@Override
	public int countTreesWithRoot(final T root) {
		final ConcurrentHashMultiset<TreeNode<T>> set = grammar.get(root);
		if (set == null) {
			return 0;
		}
		return set.size();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tsg.ITreeSubstitutionGrammar#generateRandom(codemining
	 * .lm.grammar.tree.TreeNode)
	 */
	@Override
	public TreeNode<T> generateRandom(final TreeNode<T> root) {
		checkArgument(grammar.get(root.getData()).size() > 0);

		final ArrayDeque<TreeNode<T>> toVisit = new ArrayDeque<TreeNode<T>>();
		toVisit.push(root);

		while (!toVisit.isEmpty()) {
			final TreeNode<T> currentNode = toVisit.pop();
			final boolean isNonTerminal = currentNode.nProperties() > 0;

			if (currentNode.isLeaf() && isNonTerminal) {
				// Get a random element
				final Multiset<TreeNode<T>> consequents = grammar
						.get(currentNode.getData());
				if (consequents == null) {
					continue;
				}
				final TreeNode<T> selected = SampleUtils
						.getRandomElement(consequents);
				// Copy
				final TreeNode<T> selectedCopy = selected.deepCopy();

				// add all children here!
				for (int i = 0; i < selected.nProperties(); i++) {
					final List<TreeNode<T>> propertyChildren = selectedCopy
							.getChildrenByProperty().get(i);
					for (final TreeNode<T> child : propertyChildren) {
						currentNode.addChildNode(child, i);
						toVisit.push(child);
					}
				}
			} else if (!currentNode.isLeaf()) {
				// Keep walking
				for (int j = 0; j < currentNode.getChildrenByProperty().size(); j++) {
					final List<TreeNode<T>> nodes = currentNode
							.getChildrenByProperty().get(j);
					for (final TreeNode<T> node : nodes) {
						toVisit.push(node);
					}
				}
			}

		}

		return root;
	}

	/**
	 * Return an (externally) immutable view of the TSG.
	 * 
	 * @return
	 */
	public Map<T, ? extends Multiset<TreeNode<T>>> getInternalGrammar() {
		return Collections.unmodifiableMap(grammar);
	}

	/**
	 * Returns the tree extractor, if any.
	 * 
	 * @return
	 */
	public ITreeExtractor<Integer> getTreeExtractor() {
		throw new NotImplementedException(
				"A generic TS grammar is not associated with any tree extractor.");
	}

	/**
	 * Prune the grammar.
	 * 
	 * @param threshold
	 */
	public void prune(final int threshold) {
		final ArrayList<T> toRemove = Lists.newArrayList();
		for (final Entry<T, ? extends Multiset<TreeNode<T>>> ruleHeadEntry : grammar
				.entrySet()) {
			final T ruleHead = ruleHeadEntry.getKey();
			final Multiset<TreeNode<T>> productions = ruleHeadEntry.getValue();
			if (productions.size() < threshold) {
				toRemove.add(ruleHead);
				continue;
			}

			final ArrayList<TreeNode<T>> toBeRemoved = Lists.newArrayList();
			for (final TreeNode<T> rule : productions.elementSet()) {
				if (productions.count(rule) < threshold) {
					toBeRemoved.add(rule);
				}
			}

			int sum = 0;
			for (final TreeNode<T> rule : toBeRemoved) {
				final int cnt = productions.count(rule);
				sum += cnt;
				productions.remove(rule, cnt);
			}

			productions.add(UNK_NODE, sum);
		}

		for (final T node : toRemove) {
			grammar.remove(node);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * codemining.lm.grammar.tsg.ITreeSubstitutionGrammar#removeTree(codemining
	 * .lm.grammar.tree.TreeNode)
	 */
	@Override
	public boolean removeTree(final TreeNode<T> subTree) {
		final T rootNodeData = subTree.getData();
		final ConcurrentHashMultiset<TreeNode<T>> productions = grammar
				.get(rootNodeData);
		if (productions == null) {
			return false;
		} else {
			return productions.remove(subTree);
		}
	}

	@Override
	public String toString() {
		final StringBuffer buf = new StringBuffer();
		for (final Entry<T, ? extends Multiset<TreeNode<T>>> rootEntry : grammar
				.entrySet()) {
			final T root = rootEntry.getKey();
			buf.append("********\n");
			buf.append(root.toString() + ":\n");
			for (final Multiset.Entry<TreeNode<T>> tree : rootEntry.getValue()
					.entrySet()) {
				if (tree.getElement() != null) {
					buf.append(tree.getElement().toString());
				} else {
					buf.append("null");
				}
				final double prob = ((double) tree.getCount())
						/ rootEntry.getValue().size();
				buf.append("Prob " + prob + "\n");
			}
		}
		return buf.toString();
	}
}

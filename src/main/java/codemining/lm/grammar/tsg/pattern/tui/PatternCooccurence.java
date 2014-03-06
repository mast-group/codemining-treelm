/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import java.util.Set;
import java.util.SortedSet;

import codemining.lm.grammar.tree.TreeNode;
import codemining.util.data.UnorderedPair;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Sets;

/**
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PatternCooccurence {

	/**
	 * A struct class containing the likelihood ratios of a single element
	 * 
	 * @param <T>
	 */
	public static class LikelihoodRatio implements Comparable<LikelihoodRatio> {

		public final UnorderedPair<TreeNode<Integer>> pair;

		public final double likelihoodRatio;

		public LikelihoodRatio(final UnorderedPair<TreeNode<Integer>> pair,
				final double likelihoodRatio) {
			this.pair = pair;
			this.likelihoodRatio = likelihoodRatio;
		}

		@Override
		public int compareTo(final LikelihoodRatio other) {
			return ComparisonChain.start()
					.compare(likelihoodRatio, other.likelihoodRatio)
					.compare(pair.hashCode(), other.pair.hashCode()).result();
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final LikelihoodRatio other = (LikelihoodRatio) obj;
			if (Double.doubleToLongBits(likelihoodRatio) != Double
					.doubleToLongBits(other.likelihoodRatio)) {
				return false;
			}
			if (pair == null) {
				if (other.pair != null) {
					return false;
				}
			} else if (!pair.equals(other.pair)) {
				return false;
			}
			return true;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(pair, likelihoodRatio);
		}

	}

	/**
	 * Filter the set of co-appearing patterns to remove trees that imply each
	 * other.
	 * 
	 * @param patterns
	 */
	public static void filterCoappearingPatterns(
			final SortedSet<LikelihoodRatio> patterns) {
		final Set<LikelihoodRatio> toBeRemoved = Sets.newIdentityHashSet();

		for (final LikelihoodRatio lr : patterns) {
			final TreeNode<Integer> tree1 = lr.pair.first;
			final TreeNode<Integer> tree2 = lr.pair.second;
			final Optional<TreeNode<Integer>> common = tree1
					.getMaximalOverlappingTree(tree2);
			if (!common.isPresent()) {
				continue;
			}
			final TreeNode<Integer> commonTree = common.get();
			if (commonTree.equals(tree1) || commonTree.equals(tree2)) {
				toBeRemoved.add(lr);
			}
		}

		patterns.removeAll(toBeRemoved);
	}

	/**
	 * Contains the counts of all elements.
	 */
	private final Multiset<TreeNode<Integer>> elementCount = ConcurrentHashMultiset
			.create();

	/**
	 * The co-ocurrence counts.
	 */
	private final Multiset<UnorderedPair<TreeNode<Integer>>> cooccurenceCount = ConcurrentHashMultiset
			.create();

	/**
	 * Add this set of elements to the co-occurence object.
	 * 
	 * @param elements
	 */
	final public void add(final Set<TreeNode<Integer>> elements) {
		elementCount.addAll(elements);

		final Multiset<UnorderedPair<TreeNode<Integer>>> pairs = HashMultiset
				.create();
		for (final TreeNode<Integer> element1 : elements) {
			for (final TreeNode<Integer> element2 : elements) {
				if (element1 != element2) {
					pairs.add(UnorderedPair.createUnordered(element1, element2));
				}
			}
		}

		for (final Entry<UnorderedPair<TreeNode<Integer>>> pair : pairs
				.entrySet()) {
			cooccurenceCount.add(pair.getElement(), pair.getCount() / 2);
		}
	}

	/**
	 * Return a sorted set of the most likely co-appearing elements.
	 * 
	 * @param element
	 * @return
	 */
	public final SortedSet<LikelihoodRatio> likelyCoappearingElements(
			final double minLikelihoodRatio) {
		final Set<UnorderedPair<TreeNode<Integer>>> computed = Sets
				.newHashSet();
		final SortedSet<LikelihoodRatio> likelihoods = Sets.newTreeSet();

		for (final TreeNode<Integer> element1 : elementCount.elementSet()) {
			final double pOccurenceElement1 = ((double) elementCount
					.count(element1)) / elementCount.size();
			for (final TreeNode<Integer> element2 : elementCount.elementSet()) {
				if (element1 == element2) {
					continue;
				}
				final UnorderedPair<TreeNode<Integer>> pair = UnorderedPair
						.createUnordered(element1, element2);
				if (computed.contains(pair)) {
					continue;
				} else {
					computed.add(pair);
				}

				final double pOccurenceElement2 = ((double) elementCount
						.count(element2)) / elementCount.size();

				final double pOccuringTogether = ((double) cooccurenceCount
						.count(pair)) / cooccurenceCount.size();

				final double logRatio = Math.log(pOccuringTogether)
						- Math.log(pOccurenceElement1)
						- Math.log(pOccurenceElement2);
				if (logRatio > minLikelihoodRatio) {
					likelihoods.add(new LikelihoodRatio(pair, logRatio));
				}
			}
		}

		return likelihoods;
	}

}

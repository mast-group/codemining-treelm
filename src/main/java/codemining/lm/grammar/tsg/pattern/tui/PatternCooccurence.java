/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Set;
import java.util.SortedSet;

import codemining.util.data.UnorderedPair;

import com.google.common.base.Objects;
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
public class PatternCooccurence<T> {

	/**
	 * A struct class containing the likelihood ratios of a single element
	 * 
	 * @param <T>
	 */
	public static class LikelihoodRatio<T> implements
			Comparable<LikelihoodRatio<T>> {

		public final UnorderedPair<T> pair;

		public final double likelihoodRatio;

		public LikelihoodRatio(final UnorderedPair<T> pair,
				final double likelihoodRatio) {
			this.pair = pair;
			this.likelihoodRatio = likelihoodRatio;
		}

		@Override
		public int compareTo(final LikelihoodRatio<T> other) {
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
			final LikelihoodRatio<T> other = (LikelihoodRatio<T>) obj;
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

		@Override
		public String toString() {
			return pair + ":" + String.format("%.2f", likelihoodRatio);
		}
	}

	/**
	 * Contains the counts of all elements.
	 */
	private final Multiset<T> elementCount = ConcurrentHashMultiset.create();

	/**
	 * The co-occurrence counts.
	 */
	private final Multiset<UnorderedPair<T>> cooccurenceCount = ConcurrentHashMultiset
			.create();

	/**
	 * Add this set of elements to the co-occurence object.
	 * 
	 * @param elements
	 */
	final public void add(final Set<T> elements) {
		elementCount.addAll(elements);

		final Multiset<UnorderedPair<T>> pairs = HashMultiset.create();
		for (final T element1 : elements) {
			for (final T element2 : elements) {
				if (element1 != element2) {
					pairs.add(UnorderedPair.createUnordered(element1, element2));
				}
			}
		}

		for (final Entry<UnorderedPair<T>> pair : pairs.entrySet()) {
			checkArgument(pair.getCount() / 2 > 0);
			cooccurenceCount.add(pair.getElement(), pair.getCount() / 2);
		}
	}

	/**
	 * Return a sorted set of the most likely co-appearing elements.
	 * 
	 * @param element
	 * @return
	 */
	public final SortedSet<LikelihoodRatio<T>> likelyCoappearingElements(
			final double minLikelihoodRatio) {
		final SortedSet<LikelihoodRatio<T>> likelihoods = Sets.newTreeSet();

		for (final UnorderedPair<T> pair : cooccurenceCount.elementSet()) {
			final T element1 = pair.first;
			final T element2 = pair.second;
			final int nElementCount = elementCount.size();
			final double pOccurenceElement1 = ((double) elementCount
					.count(element1)) / nElementCount;

			final double pOccurenceElement2 = ((double) elementCount
					.count(element2)) / elementCount.size();

			final double pOccuringTogether = ((double) cooccurenceCount
					.count(pair)) / cooccurenceCount.size();

			final double logRatio = Math.log(pOccuringTogether)
					- Math.log(pOccurenceElement1)
					- Math.log(pOccurenceElement2);
			if (logRatio > minLikelihoodRatio) {
				likelihoods.add(new LikelihoodRatio<T>(pair, logRatio));
			}
		}

		return likelihoods;
	}

}

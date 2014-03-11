/**
 * 
 */
package codemining.lm.grammar.tsg.pattern.tui;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;

import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

/**
 * The co-occurence of elements of different type.
 * 
 * Not thread-safe
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class ElementCooccurence<TRow, TColumn> {

	/**
	 * Struct class containing lift data.
	 * 
	 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
	 * 
	 * @param <TRow>
	 * @param <TColumn>
	 */
	public static class Lift<TRow, TColumn> implements Comparable<Lift> {
		public final double lift;
		public final TRow row;
		public final TColumn column;
		public final long count;

		private Lift(final TRow row, final TColumn column, final double lift,
				final long count) {
			this.row = row;
			this.column = column;
			this.lift = lift;
			this.count = count;
		}

		@Override
		public int compareTo(final Lift other) {
			return ComparisonChain.start().compare(other.lift, lift)
					.compare(row.hashCode(), other.row.hashCode())
					.compare(column.hashCode(), row.hashCode()).result();
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == this) {
				return true;
			}
			if (!(obj instanceof Lift)) {
				return false;
			}
			final Lift<TRow, TColumn> other = (Lift<TRow, TColumn>) obj;
			return Objects.equal(lift, other.lift)
					&& Objects.equal(row, other.row)
					&& Objects.equal(column, other.column);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(row, column, lift);
		}

		@Override
		public String toString() {
			return row + "," + column + ":" + String.format("%.2f", lift);
		}
	}

	/**
	 * Contains the counts of all elements.
	 */
	private final Multiset<TRow> tRowCount = HashMultiset.create();
	private final Multiset<TColumn> tColumnCount = HashMultiset.create();

	/**
	 * Co-occuring elements.
	 */
	private final Table<TRow, TColumn, Long> coocurenceMx = HashBasedTable
			.create();
	private long totalCooccurences = 0;

	/**
	 * Add elements.
	 * 
	 * @param rowElementSet
	 * @param columnElementSet
	 */
	final public void add(final Set<TRow> rowElementSet,
			final Set<TColumn> columnElementSet) {
		tRowCount.addAll(rowElementSet);
		tColumnCount.addAll(columnElementSet);

		for (final TRow t1Element : rowElementSet) {
			for (final TColumn t2Element : columnElementSet) {
				final Long previousCount = coocurenceMx.get(t1Element,
						t2Element);
				if (previousCount == null) {
					coocurenceMx.put(t1Element, t2Element, 1L);
				} else {
					coocurenceMx.put(t1Element, t2Element, previousCount + 1L);
				}
			}
		}
		totalCooccurences += rowElementSet.size() * columnElementSet.size();
	}

	public Multiset<TColumn> getColumnMultiset() {
		return tColumnCount;
	}

	public Set<TColumn> getColumnValues() {
		return tColumnCount.elementSet();
	}

	/**
	 * Return the co-occuring elements for the given column.
	 * 
	 * @param row
	 * @return
	 */
	public SortedSet<Lift<TRow, TColumn>> getCooccuringElementsForColumn(
			final TColumn column) {
		final SortedSet<Lift<TRow, TColumn>> elements = Sets.newTreeSet();

		final double columnLogProbability = Math.log(((double) tColumnCount
				.count(column)) / tColumnCount.size());

		final Map<TRow, Long> rows = coocurenceMx.columnMap().get(column);
		if (rows == null) {
			return elements;
		}
		for (final Entry<TRow, Long> rowEntries : rows.entrySet()) {
			final double rowProbability = ((double) tRowCount.count(rowEntries
					.getKey())) / tRowCount.size();
			final double cooccurenceProbability = ((double) rowEntries
					.getValue()) / totalCooccurences;
			final double liftP = Math.log(cooccurenceProbability)
					- Math.log(rowProbability) - columnLogProbability;

			elements.add(new Lift<TRow, TColumn>(rowEntries.getKey(), column,
					liftP, rowEntries.getValue()));
		}

		return elements;
	}

	/**
	 * Return the co-occuring elements for the given row.
	 * 
	 * @param row
	 * @return
	 */
	public SortedSet<Lift<TRow, TColumn>> getCooccuringElementsForRow(
			final TRow row) {
		final SortedSet<Lift<TRow, TColumn>> elements = Sets.newTreeSet();

		final double rowLogProbability = Math.log(((double) tRowCount
				.count(row)) / tRowCount.size());

		final Map<TColumn, Long> columns = coocurenceMx.rowMap().get(row);
		if (columns == null) {
			return elements;
		}
		for (final Entry<TColumn, Long> columnEntries : columns.entrySet()) {
			final double columnProbability = ((double) tColumnCount
					.count(columnEntries.getKey())) / tColumnCount.size();
			final double cooccurenceProbability = ((double) columnEntries
					.getValue()) / totalCooccurences;
			final double liftP = Math.log(cooccurenceProbability)
					- Math.log(columnProbability) - rowLogProbability;

			elements.add(new Lift<TRow, TColumn>(row, columnEntries.getKey(),
					liftP, columnEntries.getValue()));
		}

		return elements;
	}

	/**
	 * Return the log lift of an element.
	 * 
	 * @param row
	 * @param column
	 * @return
	 */
	public double getElementLogLift(final TRow row, final TColumn column) {
		final double columnProbability = ((double) tColumnCount.count(column))
				/ tColumnCount.size();
		final double rowProbability = ((double) tRowCount.count(row))
				/ tRowCount.size();

		final double cooccurenceProbability;
		if (coocurenceMx.contains(row, column)) {
			cooccurenceProbability = ((double) coocurenceMx.get(row, column))
					/ totalCooccurences;
		} else {
			cooccurenceProbability = 0;
		}
		return Math.log(cooccurenceProbability) - Math.log(columnProbability)
				- Math.log(rowProbability);
	}

	public Multiset<TRow> getMostPopularRowFirst() {
		return Multisets.copyHighestCountFirst(tRowCount);
	}

	public Multiset<TRow> getRowMultiset() {
		return tRowCount;
	}

	public Set<TRow> getRowValues() {
		return tRowCount.elementSet();
	}

	/**
	 * Prune all pairs that have a count lower than the given threshold.
	 * 
	 * @param threshold
	 */
	public void prune(final int threshold) {
		final List<Cell<TRow, TColumn, Long>> toBeRemoved = Lists
				.newArrayList();

		for (final Cell<TRow, TColumn, Long> cell : coocurenceMx.cellSet()) {
			if (cell.getValue() <= threshold) {
				toBeRemoved.add(cell);
			}
		}

		for (final Cell<TRow, TColumn, Long> cell : toBeRemoved) {
			coocurenceMx.remove(cell.getRowKey(), cell.getColumnKey());
		}
	}
}

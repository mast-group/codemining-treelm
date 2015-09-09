/**
 *
 */
package codemining.lm.tsg.idioms.tui;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;

import codemining.ast.TreeNode;
import codemining.ast.java.AbstractJavaTreeExtractor;
import codemining.java.codeutils.JavaASTExtractor;
import codemining.lm.tsg.FormattedTSGrammar;
import codemining.lm.tsg.idioms.PatternCorpus;
import codemining.lm.tsg.idioms.PatternStatsCalculator;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Get a file containing the Deckard clones, parse it and find the overlap with
 * the TSG.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class DeckardClonesInTsg {

	/**
	 * Convert the ASTNodes in the collection to our format.
	 */
	private static Set<TreeNode<Integer>> convertNodes(
			final Collection<ASTNode> nodes,
			final AbstractJavaTreeExtractor javaTreeExtractor) {
		final Set<TreeNode<Integer>> treeNodes = Sets.newIdentityHashSet();

		for (final ASTNode node : nodes) {
			treeNodes.add(javaTreeExtractor.getTree(node));
		}

		return treeNodes;
	}

	private static Set<TreeNode<Integer>> getClonePatterns(
			final Multimap<Integer, ASTNode> decardClones,
			final AbstractJavaTreeExtractor javaTreeExtractor) {
		final Set<TreeNode<Integer>> decardPatterns = Sets.newHashSet();

		for (final int key : decardClones.keySet()) {
			final Collection<ASTNode> nodes = decardClones.get(key);
			final Set<TreeNode<Integer>> convertedNodes = convertNodes(nodes,
					javaTreeExtractor);

			// then get the maximal common subtree
			final Iterator<TreeNode<Integer>> nodeIterator = convertedNodes
					.iterator();
			TreeNode<Integer> maximumOverlappingNode = nodeIterator.next();
			while (nodeIterator.hasNext()) {
				final Optional<TreeNode<Integer>> maximalOverlappingTree = maximumOverlappingNode
						.getMaximalOverlappingTree(nodeIterator.next());
				if (maximalOverlappingTree.isPresent()
						&& maximalOverlappingTree.get().getTreeSize() >= .9 * maximumOverlappingNode
								.getTreeSize()) {
					maximumOverlappingNode = maximalOverlappingTree.get();
				}
			}

			// and push it into decardPatterns
			decardPatterns.add(maximumOverlappingNode);
		}

		return decardPatterns;
	}

	private static Multimap<Integer, ASTNode> getClonesFromDecard(
			final String clusterFile, final File baseDirectory)
			throws IOException {
		final List<String> lines = FileUtils.readLines(new File(clusterFile));
		int id = 0;
		final Multimap<Integer, ASTNode> nodesInCluster = ArrayListMultimap
				.create();

		final JavaASTExtractor astExtractor = new JavaASTExtractor(false);

		for (final String line : lines) {
			if (line.length() == 0) {
				id++;
				continue;
			}
			final Matcher matcher = decardClone.matcher(line);
			checkArgument(matcher.find());
			final String filename = matcher.group(1);
			final int startingLine = Integer.parseInt(matcher.group(2));
			final int offestLine = Integer.parseInt(matcher.group(3));

			final File targetFile = new File(baseDirectory.getAbsolutePath()
					+ "/" + filename);
			final CompilationUnit fileAst = astExtractor.getAST(targetFile);
			final int start = fileAst.getPosition(startingLine, 0);
			final int end = fileAst.getPosition(startingLine + offestLine - 1,
					0);

			final ASTNode cloneNode = NodeFinder.perform(fileAst, start, end
					- start);
			nodesInCluster.put(id, cloneNode);
		}

		return nodesInCluster;
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws IOException,
			SerializationException {
		if (args.length != 4) {
			System.err
					.println("Usage <decardCloneClustersFile> <baseDir> <tsg> <testDir>");
			System.exit(-1);
		}

		final Multimap<Integer, ASTNode> decardClones = getClonesFromDecard(
				args[0], new File(args[1]));

		// Read tsg
		final FormattedTSGrammar grammar = (FormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[2]);
		final Set<TreeNode<Integer>> decardCloneTrees = getClonePatterns(
				decardClones,
				(AbstractJavaTreeExtractor) grammar.getTreeExtractor());

		Serializer.getSerializer().serialize(decardCloneTrees,
				"decardPatterns.ser");

		// Now check how many of them we have in our TSG
		final Set<TreeNode<Integer>> patterns = PatternCorpus.getPatternsFrom(
				grammar, 0, 0);

		final Set<TreeNode<Integer>> common = Sets.intersection(
				decardCloneTrees, patterns).immutableCopy();
		System.out.println("Common:" + common.size());
		final double pct = ((double) common.size()) / patterns.size();
		System.out.println("PctCommon:" + pct);

		final PatternStatsCalculator psc = new PatternStatsCalculator(
				(AbstractJavaTreeExtractor) grammar.getTreeExtractor(),
				decardCloneTrees, new File(args[3]));

		final int[] zeroArray = { 0 };
		psc.printStatisticsFor(zeroArray, zeroArray);

		System.out.println("Deckard Patterns");
		System.out.println("--------------------------------");
		for (final TreeNode<Integer> decardPattern : decardCloneTrees) {
			PrintPatterns.printIntTree(grammar.getTreeExtractor(),
					decardPattern);
		}

	}

	public static final Pattern decardClone = Pattern
			.compile("[0-9]{9}\\tdist:[0-9]\\.[0-9]\\tFILE\\s(\\S+\\.java)\\sLINE:([0-9]+)\\:([0-9]+)");

}

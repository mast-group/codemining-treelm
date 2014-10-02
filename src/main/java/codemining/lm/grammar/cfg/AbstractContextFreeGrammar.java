package codemining.lm.grammar.cfg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;

import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.ParseType;
import codemining.lm.ILanguageModel;
import codemining.lm.grammar.tree.AbstractTreeExtractor;
import codemining.lm.grammar.tree.AstNodeSymbol;
import codemining.lm.grammar.tree.TreeNode;
import codemining.math.random.SampleUtils;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.math.DoubleMath;

public abstract class AbstractContextFreeGrammar implements ILanguageModel {

	/**
	 * A CFG rule struct.
	 */
	public static class CFGRule implements Serializable {

		private static final long serialVersionUID = -7306875690461628508L;
		public final int root;
		public final NodeConsequent ruleConsequent;

		public CFGRule(final int from, final NodeConsequent to) {
			root = from;
			ruleConsequent = to;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
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
			final CFGRule other = (CFGRule) obj;
			return Objects.equal(root, other.root)
					&& Objects.equal(ruleConsequent, other.ruleConsequent);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hashCode(root, ruleConsequent);
		}

	}

	/**
	 * A struct class to hold the node consequents.
	 *
	 */
	public static class NodeConsequent implements Serializable {

		private static final long serialVersionUID = 472884980627103177L;
		public final List<List<Integer>> nodes;

		public NodeConsequent() {
			nodes = Lists.newArrayList();
		}

		public NodeConsequent(final int propertiesSize) {
			nodes = Lists.newArrayListWithCapacity(propertiesSize);
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == this) {
				return true;
			}
			if (!(obj instanceof NodeConsequent)) {
				return false;
			}
			final NodeConsequent object = (NodeConsequent) obj;
			return Objects.equal(object.nodes, nodes);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(nodes);
		}

		@Override
		public String toString() {
			return nodes.toString();
		}

	}

	private static final long serialVersionUID = 3019243696898888854L;

	/**
	 * The actual grammar (in symbols)
	 */
	protected Map<Integer, Multiset<NodeConsequent>> grammar;

	private final ITokenizer tokenizer = new JavaTokenizer();

	/**
	 * The tree extractor.
	 */
	protected final AbstractTreeExtractor treeExtractor;

	public AbstractContextFreeGrammar(
			final AbstractTreeExtractor treeExtractor,
			final Map<Integer, Multiset<NodeConsequent>> grammar) {
		this.treeExtractor = checkNotNull(treeExtractor);
		this.grammar = grammar;
	}

	public abstract void addCFGRule(final CFGRule rule);

	/**
	 * Add a single CFG rule to this grammar.
	 *
	 * @param rootId
	 * @param ruleConsequent
	 * @param grammar
	 */
	public abstract void addCFGRule(final int rootId,
			final NodeConsequent ruleConsequent);

	/**
	 * Add grammar rules from the given code.
	 *
	 */
	public void addGrammarRulesFromCode(final String code,
			final ParseType parseType) {
		final TreeNode<Integer> tree = treeExtractor.getTree(code, parseType);
		addRulesFrom(tree);
	}

	/**
	 * Recursively update tree frequencies. I.e. when a tree is added to the
	 * corpus, update the counts appropriately.
	 *
	 * @param node
	 */
	public abstract void addRulesFrom(final TreeNode<Integer> node);

	/**
	 * Create a single CFG rule for the given node.
	 *
	 * @param currentNode
	 * @param grammar2
	 */
	public CFGRule createCFRuleForNode(final TreeNode<Integer> currentNode) {
		final int rootId = currentNode.getData();

		final int nProperties = currentNode.nProperties();
		final NodeConsequent ruleConsequent = new NodeConsequent(nProperties);
		for (int i = 0; i < nProperties; i++) {
			final List<TreeNode<Integer>> children = currentNode
					.getChildrenByProperty().get(i);
			final int nChildren = children.size();
			ruleConsequent.nodes.add(Lists
					.<Integer> newArrayListWithCapacity(nChildren));
			for (int j = 0; j < nChildren; j++) {
				final int childNode = currentNode.getChild(j, i).getData();
				ruleConsequent.nodes.get(i).add(childNode);
			}
		}

		return new CFGRule(rootId, ruleConsequent);
	}

	/**
	 * Generate a random tree based on this CFG.
	 *
	 * @return
	 */
	public TreeNode<Integer> generateRandom() {
		final TreeNode<Integer> root = treeExtractor.getKeyForCompilationUnit();

		final ArrayDeque<TreeNode<Integer>> toVisit = new ArrayDeque<TreeNode<Integer>>();
		toVisit.push(root);

		while (!toVisit.isEmpty()) {
			final TreeNode<Integer> currentNode = toVisit.pop();
			final Multiset<NodeConsequent> productions = grammar
					.get(currentNode.getData());
			if (productions != null) {
				final NodeConsequent selected = SampleUtils
						.getRandomElement(productions);

				for (int i = 0; i < selected.nodes.size(); i++) {
					final List<Integer> nodes = selected.nodes.get(i);
					for (final int node : nodes) {
						final AstNodeSymbol symbol = treeExtractor
								.getSymbol(node);
						final TreeNode<Integer> treeNode = TreeNode.create(
								node, symbol.nChildProperties());
						currentNode.addChildNode(treeNode, i);
						toVisit.push(treeNode);
					}
				}

			}
		}

		return root;
	}

	@Override
	public double getAbsoluteEntropy(final File file) throws IOException {
		final String sourceCode = FileUtils.readFileToString(file);
		return getAbsoluteEntropy(sourceCode);
	}

	@Override
	public double getAbsoluteEntropy(final String fileContent) {
		return getAbsoluteEntropy(fileContent, ParseType.COMPILATION_UNIT);
	}

	public double getAbsoluteEntropy(final String fileContent,
			final ParseType parseType) {
		final ContextFreeGrammar otherCfg = new ContextFreeGrammar(
				treeExtractor);

		otherCfg.addGrammarRulesFromCode(fileContent, parseType);
		final double entropy = getEntropyOfRules(otherCfg);

		checkArgument(entropy != 0);
		checkArgument(!Double.isNaN(entropy));
		return entropy;
	}

	/**
	 * Return the entropy (unnormalised) of the given rules
	 *
	 * @param otherCfg
	 * @return
	 */
	public double getEntropyOfRules(final ContextFreeGrammar otherCfg) {
		double sum = 0;

		for (final Entry<Integer, Multiset<NodeConsequent>> entry : otherCfg.grammar
				.entrySet()) {
			final int fromNode = entry.getKey();
			for (final Multiset.Entry<NodeConsequent> toNodes : entry
					.getValue().entrySet()) {
				sum += toNodes.getCount()
						* DoubleMath.log2(getMLProbability(fromNode,
								toNodes.getElement()));
			}
		}

		return sum;
	}

	@Override
	public double getExtrinsticEntropy(final File file) throws IOException {
		final String fileContent = FileUtils.readFileToString(file);
		return getExtrinsticEntropy(fileContent);
	}

	@Override
	public double getExtrinsticEntropy(final String fileContent) {
		// TODO WARNING this is duplicate code!!
		final char[] code = fileContent.toCharArray();
		if (code.length == 0) {
			return 0;
		}
		final List<String> tokens = tokenizer.tokenListFromCode(code);

		if (tokens.isEmpty()) {
			return 0;
		}

		final double crossEntropy = getAbsoluteEntropy(fileContent)
				/ tokens.size();
		checkArgument(!Double.isNaN(crossEntropy));
		return crossEntropy;
	}

	public Map<Integer, Multiset<NodeConsequent>> getInternalGrammar() {
		return grammar;
	}

	/**
	 * Return the maximum likelihood probability of a rule.
	 *
	 * @param from
	 * @param to
	 * @return
	 */
	public final double getMLProbability(final int from, final NodeConsequent to) {
		final Multiset<NodeConsequent> consequents = grammar.get(from);
		if (consequents == null) {
			return 1.;
		}

		return ((double) consequents.count(to)) / consequents.size();
	}

	final AbstractTreeExtractor getTreeExtractor() {
		return treeExtractor;
	}

	@Override
	public AbstractFileFilter modelledFilesFilter() {
		return tokenizer.getFileFilter();
	}

	@Override
	public abstract void trainIncrementalModel(final Collection<File> files)
			throws IOException;

	@Override
	public abstract void trainModel(final Collection<File> trainingFiles)
			throws IOException;

}
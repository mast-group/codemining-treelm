package codemining.lm.grammar.cfg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;

import codemining.java.codeutils.JavaTokenizer;
import codemining.languagetools.ITokenizer;
import codemining.languagetools.ParseType;
import codemining.lm.ILanguageModel;
import codemining.lm.grammar.tree.ASTNodeSymbol;
import codemining.lm.grammar.tree.ITreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.math.random.SampleUtils;

import com.google.common.base.Objects;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.math.DoubleMath;

public abstract class AbstractContextFreeGrammar implements ILanguageModel {

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
	 * Add a single CFG rule
	 * 
	 * @param rootId
	 * @param ruleConsequent
	 * @param grammar
	 */
	public static void addCFGRule(final int rootId,
			final NodeConsequent ruleConsequent,
			final Map<Integer, Multiset<NodeConsequent>> grammar) {
		Multiset<NodeConsequent> ruleProduction;
		final Multiset<NodeConsequent> tempMultiset = ConcurrentHashMultiset
				.create();

		if (grammar instanceof ConcurrentMap) {
			final ConcurrentMap<Integer, Multiset<NodeConsequent>> conGrammar = (ConcurrentMap<Integer, Multiset<NodeConsequent>>) grammar;
			ruleProduction = conGrammar.putIfAbsent(rootId, tempMultiset);
		} else {
			if (grammar.containsKey(rootId)) {
				ruleProduction = grammar.get(rootId);
			} else {
				ruleProduction = null;
			}
		}
		if (ruleProduction == null) {
			ruleProduction = tempMultiset;
		}

		ruleProduction.add(ruleConsequent);
	}

	/**
	 * Create a single CFG rule for the given node.
	 * 
	 * @param currentNode
	 * @param grammar2
	 */
	public static void createCFRuleForNode(final TreeNode<Integer> currentNode,
			final Map<Integer, Multiset<NodeConsequent>> grammar) {
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

		addCFGRule(rootId, ruleConsequent, grammar);
	}

	/**
	 * Recursively update tree frequencies. I.e. when a tree is added to the
	 * corpus, update the counts appropriately.
	 * 
	 * @param node
	 */
	public static void updateRules(final TreeNode<Integer> node,
			final Map<Integer, Multiset<NodeConsequent>> grammar) {
		checkNotNull(node);

		final ArrayDeque<TreeNode<Integer>> nodeUpdates = new ArrayDeque<TreeNode<Integer>>();
		nodeUpdates.push(node);

		while (!nodeUpdates.isEmpty()) {
			final TreeNode<Integer> currentNode = nodeUpdates.pop();
			createCFRuleForNode(currentNode, grammar);

			for (final List<TreeNode<Integer>> childProperty : currentNode
					.getChildrenByProperty()) {
				for (final TreeNode<Integer> child : childProperty) {
					if (!child.isLeaf()) {
						nodeUpdates.push(child);
					}
				}
			}

		}
	}

	/**
	 * The actual grammar (in symbols)
	 */
	protected Map<Integer, Multiset<NodeConsequent>> grammar;

	private transient ITokenizer tokenizer = new JavaTokenizer();

	protected final ITreeExtractor<Integer> treeExtractor;

	public AbstractContextFreeGrammar(
			final ITreeExtractor<Integer> treeExtractor,
			final Map<Integer, Multiset<NodeConsequent>> grammar) {
		this.treeExtractor = checkNotNull(treeExtractor);
		this.grammar = grammar;
	}

	/**
	 * Get the grammar rules from code
	 * 
	 */
	public void addGrammarRulesFromCode(final String code,
			final Map<Integer, Multiset<NodeConsequent>> ruleset,
			final ParseType parseType) {
		final TreeNode<Integer> tree = treeExtractor.getTree(code,
				parseType);
		updateRules(tree, ruleset);
	}

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
						final ASTNodeSymbol symbol = treeExtractor
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
		final Map<Integer, Multiset<NodeConsequent>> rules = Maps
				.newConcurrentMap();

		addGrammarRulesFromCode(fileContent, rules, parseType);
		final double entropy = getEntropyOfRules(rules);

		checkArgument(entropy != 0);
		checkArgument(!Double.isNaN(entropy));
		return entropy;
	}

	/**
	 * Return the entropy (unnormalised) of the given rules
	 * 
	 * @param fileRules
	 * @return
	 */
	public double getEntropyOfRules(
			final Map<Integer, Multiset<NodeConsequent>> fileRules) {
		double sum = 0;

		for (final Entry<Integer, Multiset<NodeConsequent>> entry : fileRules
				.entrySet()) {
			final int fromNode = entry.getKey();
			for (final com.google.common.collect.Multiset.Entry<NodeConsequent> toNodes : entry
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

	@Override
	public ILanguageModel getImmutableVersion() {
		return new ImmutableContextFreeGrammar(this);
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

	final ITreeExtractor<Integer> getTreeExtractor() {
		return treeExtractor;
	}

	@Override
	public AbstractFileFilter modelledFilesFilter() {
		return tokenizer.getFileFilter();
	}

	private void readObject(final ObjectInputStream in) throws IOException,
			ClassNotFoundException {
		in.defaultReadObject();
		tokenizer = new JavaTokenizer();
	}

	@Override
	public abstract void trainIncrementalModel(final Collection<File> files)
			throws IOException;

	@Override
	public abstract void trainModel(final Collection<File> trainingFiles)
			throws IOException;

}
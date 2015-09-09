/**
 *
 */
package codemining.lm.grammar.tsg.idioms.tui;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;

import codemining.lm.grammar.cfg.AbstractContextFreeGrammar.CFGRule;
import codemining.lm.grammar.tree.AbstractTreeExtractor;
import codemining.lm.grammar.tree.AstNodeSymbol;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tree.TreeNode.NodeDataPair;
import codemining.lm.grammar.tsg.FormattedTSGrammar;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.TreeProbabilityComputer;
import codemining.lm.grammar.tsg.samplers.CFGPrior;
import codemining.lm.grammar.tsg.samplers.CFGPrior.IRuleCreator;
import codemining.lm.grammar.tsg.samplers.blocked.BlockCollapsedGibbsSampler;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * Rank idioms based on coverage * x-entropy gain
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class IdiomRanking {

    public class IdiomInformation {

        final double crossEntropyGain;

        final int coverage;
        final TreeNode<Integer> idiom;

        public IdiomInformation(final TreeNode<Integer> idiom, final double crossEntropyGain, final int coverage) {
            this.coverage = coverage;
            this.crossEntropyGain = crossEntropyGain;
            this.idiom = idiom;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer();
            sb.append(grammar.treeToString(TSGNode.convertTree(idiom, 0)));
            sb.append(System.lineSeparator());
            sb.append("Cross-Entropy Gain:").append(String.format("%6.3e", crossEntropyGain)).append(" Coverage:")
                    .append(coverage).append(System.lineSeparator());
            try {
                final AbstractTreeExtractor treeFormat = grammar.getTreeExtractor();
                if (treeFormat.getSymbol(idiom.getData()).nodeType == AstNodeSymbol.MULTI_NODE) {
                    treeFormat.printMultinode(sb, idiom);
                } else {
                    sb.append(treeFormat.getCodeFromTree(idiom));
                }
            } catch (final Throwable e) {
                sb.append("Cannot get AST representation of rule: " + ExceptionUtils.getFullStackTrace(e));
            }
            return sb.toString();
        }

    }

    private static final Logger LOGGER = Logger.getLogger(IdiomRanking.class.getName());

    /**
     * A predicate for comparing integer tree nodes.
     */
    public static final Predicate<NodeDataPair<Integer>> BASE_EQUALITY_COMPARATOR = new Predicate<NodeDataPair<Integer>>() {

        @Override
        public boolean apply(final NodeDataPair<Integer> nodePair) {
            return nodePair.fromNode.equals(nodePair.toNode);
        }

    };

    /**
     * @param args
     * @throws SerializationException
     */
    public static void main(final String[] args) throws SerializationException {
        if (args.length != 2) {
            System.err.println("Usage <tsg> <directoryToComputeCoverage>");
            System.exit(-1);
        }

        final FormattedTSGrammar grammar = (FormattedTSGrammar) Serializer.getSerializer().deserializeFrom(args[0]);
        final IdiomRanking ranking = new IdiomRanking(grammar);

        ranking.addCorpus(args[1]);

        final List<IdiomInformation> rankedIdioms = ranking.getRanking(25);

        int i = 1;
        for (final IdiomInformation idiomInfo : rankedIdioms) {
            System.out.println(i + ":" + idiomInfo);
            i++;
        }

    }

    private final FormattedTSGrammar grammar;
    private final CFGPrior cfgPrior;

    // For each idiom, it contains the nodes that match the idiom.
    private final Map<TreeNode<Integer>, Set<TreeNode<Integer>>> matchedNodesPerIdiom = Maps.newHashMap();

    public IdiomRanking(final FormattedTSGrammar grammar) {
        this.grammar = grammar;
        final IRuleCreator cfRuleCreator = new IRuleCreator() {

            @Override
            public CFGRule createRuleForNode(final TreeNode<TSGNode> node) {
                return BlockCollapsedGibbsSampler.createCFGRuleForNode(node);
            }
        };

        cfgPrior = new CFGPrior(grammar.getTreeExtractor(), cfRuleCreator);
        for (final Multiset<TreeNode<TSGNode>> production : grammar.getInternalGrammar().values()) {
            for (final Multiset.Entry<TreeNode<TSGNode>> rule : production.entrySet()) {
                matchedNodesPerIdiom.put(TSGNode.tsgTreeToInt(rule.getElement()), Sets.newIdentityHashSet());
            }
        }
    }

    /**
     * Add all files from a corpus
     *
     * @param corpusDirectory
     */
    public void addCorpus(final String corpusDirectory) {
        final Iterator<File> allFiles = FileUtils.iterateFiles(new File(corpusDirectory),
                grammar.getTreeExtractor().getTokenizer().getFileFilter(), DirectoryFileFilter.DIRECTORY);
        while (allFiles.hasNext()) {
            final File currentSource = allFiles.next();
            try {
                final TreeNode<Integer> tree = grammar.getTreeExtractor().getTree(currentSource);
                cfgPrior.addCFGRulesFrom(TSGNode.convertTree(tree, 0));
                addMatchingNodesToIdioms(tree);

            } catch (final IOException e) {
                LOGGER.warning("Failed to load " + currentSource + " because " + ExceptionUtils.getFullStackTrace(e));
            }
        }
    }

    private void addMatchingNodesToIdioms(final TreeNode<Integer> tree) {
        final ArrayDeque<TreeNode<Integer>> toLook = new ArrayDeque<TreeNode<Integer>>();
        toLook.push(tree);

        while (!toLook.isEmpty()) {
            final TreeNode<Integer> currentNode = toLook.pop();
            // at each node check if we have a partial match with the
            // current patterns

            for (final Entry<TreeNode<Integer>, Set<TreeNode<Integer>>> idiom : matchedNodesPerIdiom.entrySet()) {
                if (idiom.getKey().partialMatch(currentNode, BASE_EQUALITY_COMPARATOR, false)) {
                    idiom.getValue().addAll(currentNode.getOverlappingNodesWith(idiom.getKey()));
                }
            }

            // Keep visiting
            for (final List<TreeNode<Integer>> childProperties : currentNode.getChildrenByProperty()) {
                for (final TreeNode<Integer> child : childProperties) {
                    toLook.push(child);
                }
            }
        }
    }

    public List<IdiomInformation> getRanking(final int limit) {

        final TreeProbabilityComputer<TSGNode> tpc = new TreeProbabilityComputer<>(grammar, true,
                TreeProbabilityComputer.TSGNODE_MATCHER);
        final Map<TreeNode<Integer>, Double> idiomsCrossEntropyGain = Maps.newHashMap();
        for (final TreeNode<Integer> idiom : matchedNodesPerIdiom.keySet()) {
            final TreeNode<TSGNode> tsgIdiom = TSGNode.convertTree(idiom, 0);
            final double posteriorLogProb = tpc.getLog2ProbabilityOf(tsgIdiom);
            final double priorLogProb = cfgPrior.getTreeCFLog2Probability(tsgIdiom);
            idiomsCrossEntropyGain.put(idiom, (posteriorLogProb - priorLogProb) / idiom.getNumberOfProductions());
        }

        final Set<TreeNode<Integer>> coveredNodes = Sets.newIdentityHashSet();
        final List<IdiomInformation> ranking = Lists.newArrayList();
        final Set<TreeNode<Integer>> remainingIdioms = Sets.newHashSet(matchedNodesPerIdiom.keySet());

        int count = 0;
        while (!remainingIdioms.isEmpty() && count < limit) {
            // Do greedy selection.
            final TreeNode<Integer> nextTopIdiom = getTopIdiom(remainingIdioms, coveredNodes, idiomsCrossEntropyGain);
            final Set<TreeNode<Integer>> topIdiomMatchedNodes = matchedNodesPerIdiom.get(nextTopIdiom);
            checkArgument(remainingIdioms.remove(nextTopIdiom));
            final IdiomInformation info = new IdiomInformation(nextTopIdiom, idiomsCrossEntropyGain.get(nextTopIdiom),
                    topIdiomMatchedNodes.size());
            ranking.add(info);
            System.out.print(".");
            coveredNodes.addAll(topIdiomMatchedNodes);
            count++;
        }
        return ranking;
    }

    private TreeNode<Integer> getTopIdiom(final Set<TreeNode<Integer>> remainingIdioms,
            final Set<TreeNode<Integer>> coveredNodes, final Map<TreeNode<Integer>, Double> idiomsCrossEntropyGain) {
        double bestScore = Double.NEGATIVE_INFINITY;
        TreeNode<Integer> topIdiom = null;

        for (final TreeNode<Integer> idiom : remainingIdioms) {
            final int coverage = Sets.difference(matchedNodesPerIdiom.get(idiom), coveredNodes).size()
                    / idiom.getTreeSize(); // The number of times this idiom
                                           // matches
            final double score = idiomsCrossEntropyGain.get(idiom) * coverage;
            if (score > bestScore) {
                bestScore = score;
                topIdiom = idiom;
            }
        }
        return checkNotNull(topIdiom);
    }

}

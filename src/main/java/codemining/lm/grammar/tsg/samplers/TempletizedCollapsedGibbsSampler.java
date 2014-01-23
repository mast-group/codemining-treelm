/**
 * 
 */
package codemining.lm.grammar.tsg.samplers;

import codemining.lm.grammar.java.ast.TempletizedJavaTreeExtractor;
import codemining.lm.grammar.tree.ASTNodeSymbol;
import codemining.lm.grammar.tree.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.TSGNode;
import codemining.lm.grammar.tsg.TempletizedTSGrammar;

/**
 * A template-aware Gibbs sampler for the TSGs.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class TempletizedCollapsedGibbsSampler extends CollapsedGibbsSampler {

	private static final long serialVersionUID = 7351016344835333568L;

	public TempletizedCollapsedGibbsSampler(final double avgTreeSize,
			final double DPconcentration, final AbstractJavaTreeExtractor format) {
		super(avgTreeSize, DPconcentration, new TempletizedTSGrammar(format),
				new TempletizedTSGrammar(format));
	}

	@Override
	protected int postprocessIdForCFG(final TreeNode<TSGNode> node) {
		final int originalId = node.getData().nodeKey;
		final ASTNodeSymbol symbol = sampleGrammar.getTreeExtractor()
				.getSymbol(originalId);

		if (symbol.nodeType != ASTNodeSymbol.TEMPLATE_NODE) {
			return originalId;
		} else if (!symbol
				.hasAnnotation(TempletizedJavaTreeExtractor.TEMPLETIZED_VAR_PROPERTY)) {
			return originalId;
		} else {
			final String typeAnnotation = (String) symbol
					.getAnnotation(TempletizedJavaTreeExtractor.TEMPLETIZED_VAR_TYPE_PROPERTY);
			final ASTNodeSymbol newSymbol = TempletizedJavaTreeExtractor
					.constructTemplateSymbol(0, typeAnnotation);

			return sampleGrammar.getTreeExtractor().getOrAddSymbolId(newSymbol);
		}
	}
}

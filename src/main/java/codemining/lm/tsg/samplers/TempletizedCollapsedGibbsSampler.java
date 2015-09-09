/**
 *
 */
package codemining.lm.tsg.samplers;

import codemining.ast.AstNodeSymbol;
import codemining.ast.TreeNode;
import codemining.ast.java.AbstractJavaTreeExtractor;
import codemining.ast.java.TempletizedJavaTreeExtractor;
import codemining.lm.tsg.TSGNode;
import codemining.lm.tsg.TSGrammar;
import codemining.lm.tsg.TempletizedTSGrammar;

/**
 * A template-aware Gibbs sampler for the TSGs.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class TempletizedCollapsedGibbsSampler extends CollapsedGibbsSampler {

	protected static class TempletizedPosteriorComputer extends
	ClassicTsgPosteriorComputer {

		private static final long serialVersionUID = -4026044000876548039L;

		TempletizedPosteriorComputer(final TSGrammar<TSGNode> grammar,
				final double avgTreeSize, final double DPconcentration) {
			super(grammar, avgTreeSize, DPconcentration);
		}

		@Override
		protected int postprocessIdForCFG(final TreeNode<TSGNode> node) {
			final int originalId = node.getData().nodeKey;
			final AstNodeSymbol symbol = grammar.getTreeExtractor().getSymbol(
					originalId);

			if (symbol.nodeType != AstNodeSymbol.TEMPLATE_NODE) {
				return originalId;
			} else if (!symbol
					.hasAnnotation(TempletizedJavaTreeExtractor.TEMPLETIZED_VAR_PROPERTY)) {
				return originalId;
			} else {
				final String typeAnnotation = (String) symbol
						.getAnnotation(TempletizedJavaTreeExtractor.TEMPLETIZED_VAR_TYPE_PROPERTY);
				final AstNodeSymbol newSymbol = TempletizedJavaTreeExtractor
						.constructTemplateSymbol(0, typeAnnotation);

				return grammar.getTreeExtractor().getOrAddSymbolId(newSymbol);
			}
		}
	}

	private static final long serialVersionUID = 7351016344835333568L;

	public TempletizedCollapsedGibbsSampler(final double avgTreeSize,
			final double DPconcentration, final AbstractJavaTreeExtractor format) {
		super(avgTreeSize, DPconcentration, new TempletizedTSGrammar(format),
				new TempletizedTSGrammar(format));
	}

	@Override
	protected void createPosteriorComputer(final double avgTreeSize,
			final double DPconcentration, final TSGrammar<TSGNode> grammar) {
		posteriorComputer = new ClassicTsgPosteriorComputer(grammar,
				avgTreeSize, DPconcentration);
	}
}

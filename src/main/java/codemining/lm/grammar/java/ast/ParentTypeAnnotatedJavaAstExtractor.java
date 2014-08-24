/**
 * 
 */
package codemining.lm.grammar.java.ast;

import static com.google.common.base.Preconditions.checkNotNull;

import org.eclipse.jdt.core.dom.ASTNode;

import codemining.lm.grammar.tree.AstNodeSymbol;

/**
 * Annotate symbols with their parent type.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class ParentTypeAnnotatedJavaAstExtractor extends
		JavaAstTreeExtractor {

	private static final long serialVersionUID = -636216895677579526L;

	@Override
	public void annotateSymbol(final AstNodeSymbol symbol, final ASTNode node) {
		if (checkNotNull(node).getParent() != null) {
			symbol.addAnnotation("PARENT_TYPE",
					ASTNode.nodeClassForType(node.getParent().getNodeType())
							.getSimpleName());
		}
	}

}

/**
 * 
 */
package codemining.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import codemining.ast.AstNodeSymbol;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class ASTSymbolTest {

	@Test
	public void testEquals1() {
		final AstNodeSymbol symbol1 = new AstNodeSymbol(2);
		symbol1.addSimpleProperty("test", "test1");

		final AstNodeSymbol symbol2 = new AstNodeSymbol(2);
		symbol2.addSimpleProperty("test", "test1");

		final AstNodeSymbol symbol3 = new AstNodeSymbol(2);

		final AstNodeSymbol symbol4 = new AstNodeSymbol(3);
		symbol4.addSimpleProperty("test", "test1");

		assertEquals(symbol1, symbol2);
		assertEquals(symbol2, symbol1);

		assertFalse(symbol1.equals(symbol3));
		assertFalse(symbol3.equals(symbol1));
		assertFalse(symbol1.equals(symbol4));
		assertFalse(symbol4.equals(symbol1));

		symbol1.addChildProperty("sampleCh1");
		assertFalse(symbol1.equals(symbol2));
		assertFalse(symbol1.equals(symbol3));
		assertFalse(symbol1.equals(symbol4));

		symbol2.addChildProperty("sampleCh1");
		assertEquals(symbol1, symbol2);
	}

}

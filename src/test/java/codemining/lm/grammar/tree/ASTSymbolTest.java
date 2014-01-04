/**
 * 
 */
package codemining.lm.grammar.tree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class ASTSymbolTest {

	@Test
	public void testEquals1() {
		final ASTNodeSymbol symbol1 = new ASTNodeSymbol(2);
		symbol1.addSimpleProperty("test", "test1");

		final ASTNodeSymbol symbol2 = new ASTNodeSymbol(2);
		symbol2.addSimpleProperty("test", "test1");

		final ASTNodeSymbol symbol3 = new ASTNodeSymbol(2);

		final ASTNodeSymbol symbol4 = new ASTNodeSymbol(3);
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

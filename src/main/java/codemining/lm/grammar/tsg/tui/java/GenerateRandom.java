/**
 *
 */
package codemining.lm.grammar.tsg.tui.java;

import codemining.lm.grammar.tsg.FormattedTSGrammar;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * Generate random code given a serialized TSG.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class GenerateRandom {

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length != 2) {
			System.err.println("Usage <tsg> <nSamples>");
			return;
		}
		final FormattedTSGrammar grammar = (FormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);

		for (int i = 0; i < Integer.parseInt(args[1]); i++) {
			System.out.println(grammar.generateRandomCode());
			System.out.println("-----------------------------------");
		}
	}

}

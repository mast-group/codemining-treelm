/**
 * 
 */
package codemining.lm.grammar.tsg.tui.java;

import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
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
		final JavaFormattedTSGrammar grammar = (JavaFormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);

		for (int i = 0; i < Integer.parseInt(args[1]); i++) {
			System.out.println(grammar.generateRandom().toString());
			System.out.println("-----------------------------------");
		}
	}

}

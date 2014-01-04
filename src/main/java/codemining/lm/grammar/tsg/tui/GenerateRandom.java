/**
 * 
 */
package codemining.lm.grammar.tsg.tui;

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
	public static void main(String[] args) throws SerializationException {
		if (args.length != 1) {
			System.err.println("Usage <nSamples>");
			return;
		}
		final JavaFormattedTSGrammar grammar = (JavaFormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom("sampler.ser");

		for (int i = 0; i < Integer.parseInt(args[0]); i++) {
			System.out.println(grammar.generateRandom().toString());
			System.out.println("-----------------------------------");
		}
	}

}

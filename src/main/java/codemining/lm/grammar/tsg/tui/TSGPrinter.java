/**
 * 
 */
package codemining.lm.grammar.tsg.tui;

import codemining.lm.grammar.tsg.JavaFormattedTSGrammar;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * Output a full TSG to stdout
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class TSGPrinter {
	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length < 1) {
			System.err.println("Usage <tsg>");
			System.exit(-1);
		}
		final JavaFormattedTSGrammar grammar = (JavaFormattedTSGrammar) Serializer
				.getSerializer().deserializeFrom(args[0]);
		grammar.prune(2);
		System.out.println(grammar.toString());

	}

	private TSGPrinter() {
	}

}

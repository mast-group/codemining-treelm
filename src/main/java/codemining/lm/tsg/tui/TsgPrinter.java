/**
 *
 */
package codemining.lm.tsg.tui;

import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.lm.tsg.TSGNode;
import codemining.lm.tsg.TSGrammar;
import codemining.util.serialization.Serializer;

/**
 * Output a full TSG to stdout
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class TsgPrinter {
	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {
		if (args.length < 1) {
			System.err.println("Usage <tsg>");
			System.exit(-1);
		}
		final TSGrammar<TSGNode> grammar = (TSGrammar<TSGNode>) Serializer
				.getSerializer().deserializeFrom(args[0]);
		grammar.prune(2);
		System.out.println(grammar.toString());

	}

	private TsgPrinter() {
	}

}

/**
 * 
 */
package codemining.lm.grammar.tsg.samplers.tui;

import java.io.IOException;

import codemining.lm.grammar.tsg.samplers.AbstractCollapsedGibbsSampler;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * The print the join probabilities along with the rest of the tree, given a
 * sampler state.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PrintSamplingProbabilities {

	/**
	 * @param args
	 * @throws SerializationException
	 * @throws IOException
	 */
	public static void main(final String[] args) throws SerializationException,
			IOException {
		if (args.length != 1) {
			System.err.println("Usage <sampler>");
			System.exit(-1);
		}

		final AbstractCollapsedGibbsSampler sampler = (AbstractCollapsedGibbsSampler) Serializer
				.getSerializer().deserializeFrom(args[0]);
		sampler.printCorpusProbs();

	}

}

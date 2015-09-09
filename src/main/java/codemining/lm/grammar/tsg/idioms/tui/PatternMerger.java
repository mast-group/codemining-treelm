/**
 * 
 */
package codemining.lm.grammar.tsg.idioms.tui;

import codemining.lm.grammar.java.ast.AbstractJavaTreeExtractor;
import codemining.lm.grammar.tree.TreeNode;
import codemining.lm.grammar.tsg.idioms.PatternCorpus;
import codemining.lm.grammar.tsg.tui.TsgMerger;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

/**
 * Merge multiple pattern corpora into one.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class PatternMerger {

	private static void addPatternsFrom(final PatternCorpus fromPatterns,
			final PatternCorpus toPatterns) {
		final AbstractJavaTreeExtractor fromFormat = fromPatterns.getFormat();
		final AbstractJavaTreeExtractor toFormat = toPatterns.getFormat();

		for (final TreeNode<Integer> pattern : fromPatterns.getPatterns()) {
			toPatterns.addPattern(TsgMerger.convert(pattern, fromFormat,
					toFormat));
		}

	}

	/**
	 * @param args
	 * @throws SerializationException
	 */
	public static void main(final String[] args) throws SerializationException {

		if (args.length < 3) {
			System.err.println("Usage <output.ser> <input.ser>...");
			System.exit(-1);
		}

		final PatternCorpus patterns = (PatternCorpus) Serializer
				.getSerializer().deserializeFrom(args[1]);

		for (int i = 2; i < args.length; i++) {
			final PatternCorpus otherPatterns = (PatternCorpus) Serializer
					.getSerializer().deserializeFrom(args[i]);
			addPatternsFrom(otherPatterns, patterns);
		}

		Serializer.getSerializer().serialize(patterns, args[0]);

	}

}

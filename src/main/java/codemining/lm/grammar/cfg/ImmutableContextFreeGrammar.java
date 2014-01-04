/**
 * 
 */
package codemining.lm.grammar.cfg;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import codemining.lm.ILanguageModel;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;

/**
 * An immutable CFG.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
@DefaultSerializer(JavaSerializer.class)
public class ImmutableContextFreeGrammar extends AbstractContextFreeGrammar {

	private static final long serialVersionUID = 8744879368299806736L;

	public ImmutableContextFreeGrammar(final AbstractContextFreeGrammar original) {
		super(original.treeExtractor, null);
		grammar = getImmutableCopy(original.grammar);
	}

	private Map<Integer, Multiset<NodeConsequent>> getImmutableCopy(
			final Map<Integer, Multiset<NodeConsequent>> grammar) {
		final Map<Integer, Multiset<NodeConsequent>> copyGrammar = Maps
				.newHashMap();

		for (final Entry<Integer, Multiset<NodeConsequent>> production : grammar
				.entrySet()) {
			copyGrammar.put(production.getKey(),
					ImmutableMultiset.copyOf(production.getValue()));
		}

		return ImmutableMap.copyOf(copyGrammar);
	}

	@Override
	public ILanguageModel getImmutableVersion() {
		return this;
	}

	@Override
	public void trainIncrementalModel(final Collection<File> files)
			throws IOException {
		throw new IllegalArgumentException("Immutable CFG cannot be trained");
	}

	@Override
	public void trainModel(final Collection<File> trainingFiles)
			throws IOException {
		throw new IllegalArgumentException("Immutable CFG cannot be trained");
	}

}

/**
 *
 */
package codemining.lm.grammar.js.ast;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.wst.jsdt.core.dom.ASTNode;
import org.eclipse.wst.jsdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.wst.jsdt.core.dom.ChildPropertyDescriptor;
import org.eclipse.wst.jsdt.core.dom.SimplePropertyDescriptor;
import org.eclipse.wst.jsdt.core.dom.StructuralPropertyDescriptor;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Use the reflection API to get Eclipse Javascript AST Node information on the
 * structural properties.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class JavascriptAstPropertiesData {

	/**
	 * Compute (via Java reflect API) the structural properties of the given
	 * ASTNode type.
	 *
	 * @param astNodeType
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	private static synchronized final void computeFieldsForType(
			final int astNodeType) throws IllegalArgumentException,
			IllegalAccessException {
		if (astNodeSimpleProperties.containsKey(astNodeType)) {
			// we have computed this
			return;
		}
		final List<SimplePropertyDescriptor> simpleProperties = Lists
				.newArrayList();

		final List<StructuralPropertyDescriptor> childProperties = Lists
				.newArrayList();

		try {
			final Field[] classFields = ASTNode.nodeClassForType(astNodeType)
					.getFields();

			for (final Field field : classFields) {
				if (!Modifier.isStatic(field.getModifiers())) {
					continue;
				}

				if (field.getType().equals(SimplePropertyDescriptor.class)) {
					final SimplePropertyDescriptor descriptor = (SimplePropertyDescriptor) field
							.get(null);
					simpleProperties.add(descriptor);
				} else if (field.getType()
						.equals(ChildPropertyDescriptor.class)) {
					final ChildPropertyDescriptor descriptor = (ChildPropertyDescriptor) field
							.get(null);
					childProperties.add(descriptor);
				} else if (field.getType().equals(
						ChildListPropertyDescriptor.class)) {
					final ChildListPropertyDescriptor descriptor = (ChildListPropertyDescriptor) field
							.get(null);
					childProperties.add(descriptor);
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Failed to compute fields for node type"
					+ astNodeType + ExceptionUtils.getFullStackTrace(e));
		}

		// In the end, so that if a client gets a lists, it's the final one.
		astNodeChildProperties.put(astNodeType, childProperties);
		astNodeSimpleProperties.put(astNodeType, simpleProperties);
	}

	/**
	 * Return the child properties of the given AST node type.
	 *
	 * @param astNodeType
	 * @return
	 * @throws Exception
	 */
	public static List<StructuralPropertyDescriptor> getChildProperties(
			final int astNodeType) throws Exception {
		List<StructuralPropertyDescriptor> descriptors = astNodeChildProperties
				.get(astNodeType);
		if (descriptors != null) {
			return descriptors;
		}

		// Else compute
		try {
			computeFieldsForType(astNodeType);
			descriptors = astNodeChildProperties.get(astNodeType);
		} catch (IllegalArgumentException e) {
			LOGGER.severe("Could not compute data for ASTNode type "
					+ ExceptionUtils.getFullStackTrace(e));
			throw new Exception(e);
		} catch (IllegalAccessException e) {
			LOGGER.severe("Could not compute data for ASTNode type "
					+ ExceptionUtils.getFullStackTrace(e));
			throw new Exception(e);
		}
		return descriptors;
	}

	/**
	 * Get the simple properties of the given ast node type.
	 *
	 * @param astNodeType
	 * @return
	 * @throws Exception
	 */
	public static List<SimplePropertyDescriptor> getSimpleProperties(
			final int astNodeType) throws Exception {
		List<SimplePropertyDescriptor> descriptors = astNodeSimpleProperties
				.get(astNodeType);
		if (descriptors != null) {
			return descriptors;
		}

		// Else compute
		try {
			computeFieldsForType(astNodeType);
			descriptors = astNodeSimpleProperties.get(astNodeType);
		} catch (IllegalArgumentException e) {
			LOGGER.severe("Could not compute data for ASTNode type "
					+ ExceptionUtils.getFullStackTrace(e));
			throw new Exception(e);
		} catch (IllegalAccessException e) {
			LOGGER.severe("Could not compute data for ASTNode type "
					+ ExceptionUtils.getFullStackTrace(e));
			throw new Exception(e);
		}
		return descriptors;
	}

	/**
	 * Holds the child properties of all ASTNode types.
	 */
	private static ConcurrentMap<Integer, List<StructuralPropertyDescriptor>> astNodeChildProperties = Maps
			.newConcurrentMap();

	/**
	 * Holds the simple properties of all ASTNode types.
	 */
	private static ConcurrentMap<Integer, List<SimplePropertyDescriptor>> astNodeSimpleProperties = Maps
			.newConcurrentMap();

	private static final Logger LOGGER = Logger
			.getLogger(JavascriptAstPropertiesData.class.getName());

	private JavascriptAstPropertiesData() {
	}
}

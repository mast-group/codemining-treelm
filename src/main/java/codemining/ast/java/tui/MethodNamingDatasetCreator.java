/**
 *
 */
package codemining.ast.java.tui;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import com.google.common.collect.Lists;
import com.google.gson.Gson;

import codemining.ast.AstNodeSymbol;
import codemining.ast.TreeNode;
import codemining.ast.java.JavaAstTreeExtractor;
import codemining.java.codeutils.MethodExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.util.data.Pair;

/**
 * Extract into the AST of a method in JSON along with the method name.
 *
 * @author Miltos Allamanis
 *
 */
public class MethodNamingDatasetCreator {

	static class MethodNameWithAst {
		final String filename;
		final String methodName;
		final Tree methodAst;

		MethodNameWithAst(String filename, String methodName, Tree methodAst) {
			this.filename = filename;
			this.methodName = methodName;
			this.methodAst = methodAst;
		}

	}

	static class Tree {
		public final String name;
		public final List<Tree> children = Lists.newArrayList();

		private Tree(String nodeName) {
			this.name = nodeName;
		}
	}

	public static final String SELF_TOKEN = "%SELF%";

	public static List<MethodNameWithAst> getDataset(File inputFolder) {
		Collection<File> codeFiles = FileUtils.listFiles(inputFolder, JavaTokenizer.javaCodeFileFilter,
				DirectoryFileFilter.DIRECTORY);
		return codeFiles.parallelStream().flatMap(f -> getMethods(f, inputFolder)).collect(Collectors.toList());
	}

	private static Tree getMethodAst(final MethodDeclaration method) {
		JavaAstTreeExtractor ex = new JavaAstTreeExtractor();
		TreeNode<Integer> treeNode = ex.getTree(method.getBody());
		return treeNodeToTree(treeNode, ex);
	}

	private static Stream<MethodNameWithAst> getMethods(File file, File inputFolder) {
		try {
			return MethodExtractor.getMethods(file).stream()
					.filter(m -> m != null && m.getBody() != null && !m.isConstructor()).map(m -> {
						final Tree tokens = getMethodAst(m);
						return new MethodNameWithAst(file.toString().substring(inputFolder.toString().length()),
								m.getName().toString(), tokens);
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Stream.empty();
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("Usage <inputFolder> <outputFile>");
			System.exit(-1);
		}
		final String inputFolder = args[0];
		final String outputFile = args[1];

		List<MethodNameWithAst> dataset = getDataset(new File(inputFolder));
		final FileWriter writer = new FileWriter(outputFile);
		try {
			final Gson gson = new Gson();
			gson.toJson(dataset, writer);
		} finally {
			writer.close();
		}
	}

	private static Tree treeNodeToTree(final TreeNode<Integer> treeNode, JavaAstTreeExtractor ex) {
		Tree root = new Tree(ASTNode.nodeClassForType(ex.getSymbol(treeNode.getData()).nodeType).getSimpleName());
		final Deque<Pair<TreeNode<Integer>, Tree>> toVisit = new ArrayDeque<>();

		toVisit.push(Pair.create(treeNode, root));
		while (!toVisit.isEmpty()) {
			final Pair<TreeNode<Integer>, Tree> current = toVisit.pop();

			final Tree currentTree = current.second;
			final TreeNode<Integer> currentTreeNode = current.first;

			final AstNodeSymbol astSymbol = ex.getSymbol(currentTreeNode.getData());
			for (final String simpleProperty : astSymbol.getSimpleProperties()) {
				Tree property = new Tree(simpleProperty);
				currentTree.children.add(property);

				Tree propertyValue = new Tree(astSymbol.getSimpleProperty(simpleProperty).toString());
				property.children.add(propertyValue);
			}

			for (int i = 0; i < astSymbol.nChildProperties(); i++) {
				String propertyName = astSymbol.getChildProperty(i);
				Tree propertyTree = new Tree(propertyName);
				currentTree.children.add(propertyTree);

				for (final TreeNode<Integer> child : currentTreeNode.getChildrenByProperty().get(i)) {
					final Tree childTree = new Tree(
							ASTNode.nodeClassForType(ex.getSymbol(child.getData()).nodeType).getSimpleName());
					propertyTree.children.add(childTree);
					toVisit.push(Pair.create(child, childTree));
				}
			}
		}
		return root;
	}

}

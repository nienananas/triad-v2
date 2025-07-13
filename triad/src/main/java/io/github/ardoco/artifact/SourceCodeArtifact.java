/* Licensed under MIT 2025. */
package io.github.ardoco.artifact;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterJava;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.*;
import edu.stanford.nlp.util.*;

import io.github.ardoco.Biterm.Biterm;

public class SourceCodeArtifact extends Artifact {

    private static final Logger logger = LoggerFactory.getLogger(SourceCodeArtifact.class);

    private static final TSParser parser = new TSParser();
    private static final TSLanguage javaLanguage = new TreeSitterJava();

    static {
        parser.setLanguage(javaLanguage);
    }

    public SourceCodeArtifact(String identifier, String textBody) {
        super(identifier, textBody);
    }

    @Override
    protected void preProcessing() {
        logger.info("Preprocessing Code Artifact");
    }

    @Override
    public Set<Biterm> getBiterms() {
        if (this.biterms != null) {
            return this.biterms;
        }

        Set<Biterm> identifiers = new HashSet<>();

        TSTree tree = parser.parseString(null, this.textBody);
        TSNode rootNode = tree.getRootNode();

        // Recursively traverse the AST
        extractElementsRecursive(rootNode, identifiers, this.textBody);

        this.biterms = identifiers;
        return this.biterms;
    }

    private void extractElementsRecursive(TSNode node, Set<Biterm> elements, String sourceCode) {
        String nodeType = node.getType();
        // logger.debug("Visiting node: {} -> {}", nodeType, getNodeText(node, sourceCode)); // Debug

        switch (nodeType) {
            case "class_declaration":
            case "interface_declaration":
            case "enum_declaration":
                TSNode classNameNode = findChildNodeByType(node, "identifier");
                if (classNameNode != null) {
                    elements.addAll(getBitermsFromIdentifier(getNodeText(classNameNode, sourceCode), 2));
                }
                break;

            case "method_declaration":
                TSNode methodNameNode = findChildNodeByType(node, "identifier");
                if (methodNameNode != null) {
                    elements.addAll(getBitermsFromIdentifier(getNodeText(methodNameNode, sourceCode), 2));
                }
                TSNode parametersNode = findChildNodeByType(node, "formal_parameters");
                if (parametersNode != null) {
                    extractParameters(parametersNode, elements, sourceCode);
                }
                break;

            case "field_declaration":
                extractFieldInfo(node, elements, sourceCode);
                break;

            case "method_invocation":
                // Method invocations can be complex: simpleIdentifier, fieldAccess.method, etc.
                TSNode methodInvocationNameNode = getMethodInvocationNameNode(node);
                if (methodInvocationNameNode != null) {
                    elements.addAll(getBitermsFromIdentifier(getNodeText(methodInvocationNameNode, sourceCode), 1));
                }
                break;

            case "block_comment":
                String blockCommentText = getNodeText(node, sourceCode);
                String cleanedBlockComment = blockCommentText;

                if (cleanedBlockComment.startsWith("/*") && cleanedBlockComment.endsWith("*/")) {
                    cleanedBlockComment = cleanedBlockComment
                            .substring(2, cleanedBlockComment.length() - 2)
                            .trim();

                    String[] lines = cleanedBlockComment.split("\r\n|\r|\n");
                    StringBuilder sb = new StringBuilder();
                    boolean firstContentLine = true;
                    for (String line : lines) {
                        String currentLine = line.trim();
                        if (currentLine.startsWith("*")) {
                            currentLine = currentLine.substring(1).trim();
                        }
                        if (!currentLine.isEmpty() || !sb.isEmpty()) {
                            if (!firstContentLine) {
                                sb.append("\n");
                            }
                            sb.append(currentLine);
                            firstContentLine = false;
                        }
                    }
                    cleanedBlockComment = sb.toString().trim();
                    Set<Biterm> biterms = getBitermsFromText(cleanedBlockComment);
                    elements.addAll(biterms);
                }
                break;
        }

        // Recurse for children
        for (int i = 0; i < node.getChildCount(); i++) {
            extractElementsRecursive(node.getChild(i), elements, sourceCode);
        }
    }

    private void extractParameters(TSNode parametersNode, Set<Biterm> elements, String sourceCode) {
        for (int i = 0; i < parametersNode.getChildCount(); i++) {
            TSNode child = parametersNode.getChild(i);
            if ("formal_parameter".equals(child.getType())) {
                TSNode paramTypeNode = null;
                TSNode paramNameNode = null;

                for (int j = 0; j < child.getChildCount(); j++) {
                    TSNode grandChild = child.getChild(j);
                    String gcType = grandChild.getType();
                    if (isTypeNode(gcType)) {
                        if (paramTypeNode == null) paramTypeNode = grandChild;
                    } else if ("identifier".equals(gcType)) {
                        paramNameNode = grandChild;
                    } else if ("array_type".equals(gcType)) {
                        paramTypeNode = grandChild;
                    }
                }

                if (paramTypeNode == null && child.getChildCount() >= 2) {
                    TSNode first = child.getChild(0);
                    TSNode second = child.getChild(1);
                    if (isTypeNode(first.getType()) && "identifier".equals(second.getType())) {
                        paramTypeNode = first;
                        paramNameNode = second;
                    }
                }

                if (paramNameNode != null && paramTypeNode != null) {
                    String paramName = getNodeText(paramNameNode, sourceCode);
                    String paramType = getNodeText(paramTypeNode, sourceCode);
                    elements.addAll(getBitermsFromIdentifier(paramName, 1));
                    elements.addAll(getBitermsFromIdentifier(paramType, 1));
                }
            }
        }
    }

    private boolean isTypeNode(String nodeType) {
        return "type_identifier".equals(nodeType)
                || "primitive_type".equals(nodeType)
                || "generic_type".equals(nodeType)
                || "scoped_type_identifier".equals(nodeType)
                || "array_type".equals(nodeType);
    }

    private void extractFieldInfo(TSNode fieldDeclarationNode, Set<Biterm> elements, String sourceCode) {
        TSNode typeNode = null;
        List<TSNode> nameNodes = new ArrayList<>();

        for (int i = 0; i < fieldDeclarationNode.getChildCount(); i++) {
            TSNode child = fieldDeclarationNode.getChild(i);
            String childType = child.getType();
            if (isTypeNode(childType)) {
                if (typeNode == null) typeNode = child;
            } else if ("variable_declarator_list".equals(childType) || "variable_declarator".equals(childType)) {
                if ("variable_declarator_list".equals(childType)) {
                    for (int j = 0; j < child.getChildCount(); j++) {
                        TSNode declaratorChild = child.getChild(j);
                        if ("variable_declarator".equals(declaratorChild.getType())) {
                            TSNode idNode = findChildNodeByType(declaratorChild, "identifier");
                            if (idNode != null) nameNodes.add(idNode);
                        }
                    }
                } else {
                    TSNode idNode = findChildNodeByType(child, "identifier");
                    if (idNode != null) nameNodes.add(idNode);
                }
            }
        }

        if (typeNode == null) {
            typeNode = findChildNodeByPredicate(fieldDeclarationNode, n -> isTypeNode(n.getType()));
        }

        if (typeNode != null) {
            String fieldType = getNodeText(typeNode, sourceCode);
            elements.addAll(getBitermsFromIdentifier(fieldType, 1));

            if (nameNodes.isEmpty()) {
                TSNode directNameNode = findChildNodeByTypeRecursive(fieldDeclarationNode, "identifier");
                if (directNameNode != null
                        && !getNodeText(directNameNode, sourceCode).equals(fieldType)) {
                    nameNodes.add(directNameNode);
                }
            }

            for (TSNode nameNode : nameNodes) {
                String fieldName = getNodeText(nameNode, sourceCode);
                elements.addAll(getBitermsFromIdentifier(fieldName, 1));
                elements.addAll(getBitermsFromIdentifier(fieldType, 1));
            }
        }
    }

    private TSNode getMethodInvocationNameNode(TSNode methodInvocationNode) {
        TSNode nameNode = findChildNodeByType(methodInvocationNode, "identifier");
        if (nameNode != null) return nameNode;

        TSNode fieldAccessNode = findChildNodeByType(methodInvocationNode, "field_access");
        if (fieldAccessNode != null) {
            return findLastChildNodeByType(fieldAccessNode, "identifier");
        }

        TSNode scopedIdentifierNode = findChildNodeByType(methodInvocationNode, "scoped_identifier");
        if (scopedIdentifierNode != null) {
            return findLastChildNodeByType(scopedIdentifierNode, "identifier");
        }
        return null;
    }

    private String getNodeText(TSNode node, String sourceCode) {
        if (node == null) return "";
        return sourceCode.substring(node.getStartByte(), node.getEndByte());
    }

    private TSNode findChildNodeByType(TSNode parent, String type) {
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            TSNode child = parent.getChild(i);
            if (type.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }

    private TSNode findChildNodeByPredicate(TSNode parent, java.util.function.Predicate<TSNode> predicate) {
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            TSNode child = parent.getChild(i);
            if (predicate.test(child)) {
                return child;
            }
        }
        return null;
    }

    private TSNode findLastChildNodeByType(TSNode parent, String type) {
        if (parent == null) return null;
        TSNode foundNode = null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            TSNode child = parent.getChild(i);
            if (type.equals(child.getType())) {
                foundNode = child;
            }
        }
        return foundNode;
    }

    private TSNode findChildNodeByTypeRecursive(TSNode parent, String type) {
        if (parent == null) return null;
        Queue<TSNode> queue = new LinkedList<>();
        queue.add(parent);

        while (!queue.isEmpty()) {
            TSNode current = queue.poll();
            if (type.equals(current.getType()) && current != parent) {
                return current;
            }
            for (int i = 0; i < current.getChildCount(); i++) {
                queue.add(current.getChild(i));
            }
        }
        return null;
    }

    private Set<Biterm> getBitermsFromIdentifier(String identifier, int weight) {
        Set<Biterm> biterms = new HashSet<>();
        int lastWordIndex = 0;
        List<String> terms = new LinkedList<>();
        for (int i = 1; i < identifier.length() - 1; i++) {
            if (Character.isUpperCase(identifier.charAt(i)) && Character.isLowerCase(identifier.charAt(i - 1))) {
                String term = identifier.substring(lastWordIndex, i);
                terms.add(term);
                lastWordIndex = i;
            }
        }
        if (lastWordIndex < identifier.length() - 1) {
            String term = identifier.substring(lastWordIndex, identifier.length());
            terms.add(term);
        }

        for (int i = 0; i < terms.size() - 1; i++) {
            for (int j = i + 1; j < terms.size(); j++) {
                Biterm b = new Biterm(terms.get(i), terms.get(j));
                b.setWeight(weight);
                biterms.add(b);
            }
        }
        return biterms;
    }
}

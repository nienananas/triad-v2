/* Licensed under MIT 2025. */
package io.github.ardoco.triad.model;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterJava;

import io.github.ardoco.triad.text.TextProcessor;

public class JavaCodeArtifact extends Artifact {

    private static final Logger logger = LoggerFactory.getLogger(JavaCodeArtifact.class);

    private static final TSParser parser = new TSParser();
    private static final TSLanguage javaLanguage = new TreeSitterJava();

    static {
        parser.setLanguage(javaLanguage);
    }

    public JavaCodeArtifact(String identifier, String textBody) {
        super(identifier, textBody);
    }

    public JavaCodeArtifact(JavaCodeArtifact other) {
        super(other);
    }

    @Override
    public Artifact deepCopy() {
        return new JavaCodeArtifact(this);
    }

    @Override
    protected void preProcessing() {
        logger.info("Preprocessing Code Artifact");
    }

    private String cachedProcessedTextBody;

    @Override
    public String getTextBody() {
        if (cachedProcessedTextBody != null) {
            return cachedProcessedTextBody;
        }

        StringBuilder bagOfWords = new StringBuilder();
        try {
            TSTree tree = parser.parseString(null, this.textBody);
            TSNode rootNode = tree.getRootNode();
            collectTermsForIR(rootNode, bagOfWords, this.textBody);
        } catch (Exception t) {
            // Fallback to generic text processing if parsing fails
            String processed = TextProcessor.processText(this.textBody);
            bagOfWords.append(processed);
        }

        cachedProcessedTextBody = bagOfWords.toString().trim();
        return cachedProcessedTextBody;
    }

    @Override
    public Set<Biterm> getBiterms() {
        if (this.biterms != null) {
            return this.biterms;
        }

        Map<Biterm, Integer> bitermFrequencies = new HashMap<>();
        TSTree tree = parser.parseString(null, this.textBody);
        TSNode rootNode = tree.getRootNode();

        extractElementsRecursive(rootNode, bitermFrequencies, this.textBody);

        Set<Biterm> finalBiterms = new HashSet<>();
        for (Map.Entry<Biterm, Integer> entry : bitermFrequencies.entrySet()) {
            Biterm b = entry.getKey();
            b.setWeight(entry.getValue());
            finalBiterms.add(b);
        }
        this.biterms = finalBiterms;
        return this.biterms;
    }

    private void extractElementsRecursive(TSNode node, Map<Biterm, Integer> bitermFrequencies, String sourceCode) {
        String nodeType = node.getType();
        switch (nodeType) {
            case "class_declaration":
            case "interface_declaration":
            case "enum_declaration":
                TSNode classNameNode = findChildNodeByType(node, "identifier");
                if (classNameNode != null) {
                    updateFrequencies(
                            bitermFrequencies, getTermsFromIdentifier(getNodeText(classNameNode, sourceCode)), 2);
                }
                break;

            case "method_declaration":
                TSNode methodNameNode = findChildNodeByType(node, "identifier");
                if (methodNameNode != null) {
                    updateFrequencies(
                            bitermFrequencies, getTermsFromIdentifier(getNodeText(methodNameNode, sourceCode)), 2);
                }
                TSNode parametersNode = findChildNodeByType(node, "formal_parameters");
                if (parametersNode != null) {
                    extractParameters(parametersNode, bitermFrequencies, sourceCode);
                }
                break;

            case "field_declaration":
                extractFieldInfo(node, bitermFrequencies, sourceCode);
                break;

            case "method_invocation":
                TSNode methodInvocationNameNode = getMethodInvocationNameNode(node);
                if (methodInvocationNameNode != null) {
                    updateFrequencies(
                            bitermFrequencies,
                            getTermsFromIdentifier(getNodeText(methodInvocationNameNode, sourceCode)),
                            1);
                }
                break;

            case "block_comment":
                String blockCommentText = getNodeText(node, sourceCode);
                String cleanedBlockComment = cleanJavaDoc(blockCommentText);
                if (!cleanedBlockComment.isEmpty()) {
                    Set<Biterm> commentBiterms = getBitermsFromText(cleanedBlockComment);
                    for (Biterm b : commentBiterms) {
                        bitermFrequencies.merge(b, b.getWeight(), Integer::sum);
                    }
                }
                break;
            default:
                break;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            extractElementsRecursive(node.getChild(i), bitermFrequencies, sourceCode);
        }
    }

    private void collectTermsForIR(TSNode node, StringBuilder out, String sourceCode) {
        String nodeType = node.getType();
        switch (nodeType) {
            case "class_declaration":
            case "interface_declaration":
            case "enum_declaration": {
                TSNode classNameNode = findChildNodeByType(node, "identifier");
                if (classNameNode != null) {
                    appendTerms(out, getTermsFromIdentifier(getNodeText(classNameNode, sourceCode)));
                }
                break;
            }
            case "method_declaration": {
                TSNode methodNameNode = findChildNodeByType(node, "identifier");
                if (methodNameNode != null) {
                    appendTerms(out, getTermsFromIdentifier(getNodeText(methodNameNode, sourceCode)));
                }
                TSNode parametersNode = findChildNodeByType(node, "formal_parameters");
                if (parametersNode != null) {
                    collectParameterTerms(parametersNode, out, sourceCode);
                }
                break;
            }
            case "field_declaration": {
                collectFieldTerms(node, out, sourceCode);
                break;
            }
            case "method_invocation": {
                TSNode methodInvocationNameNode = getMethodInvocationNameNode(node);
                if (methodInvocationNameNode != null) {
                    appendTerms(out, getTermsFromIdentifier(getNodeText(methodInvocationNameNode, sourceCode)));
                }
                break;
            }
            case "block_comment": {
                String blockCommentText = getNodeText(node, sourceCode);
                String cleanedBlockComment = cleanJavaDoc(blockCommentText);
                if (!cleanedBlockComment.isEmpty()) {
                    String processed = TextProcessor.processText(cleanedBlockComment);
                    if (!processed.isEmpty()) {
                        out.append(processed).append(' ');
                    }
                }
                break;
            }
            default:
                break;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collectTermsForIR(node.getChild(i), out, sourceCode);
        }
    }

    private void collectParameterTerms(TSNode parametersNode, StringBuilder out, String sourceCode) {
        for (int i = 0; i < parametersNode.getChildCount(); i++) {
            TSNode child = parametersNode.getChild(i);
            if ("formal_parameter".equals(child.getType())) {
                TSNode paramTypeNode = findChildNodeByPredicate(child, n -> isTypeNode(n.getType()));
                TSNode paramNameNode = findChildNodeByType(child, "identifier");

                if (paramNameNode != null) {
                    appendTerms(out, getTermsFromIdentifier(getNodeText(paramNameNode, sourceCode)));
                }
                if (paramTypeNode != null) {
                    appendTerms(out, getTermsFromIdentifier(getNodeText(paramTypeNode, sourceCode)));
                }
            }
        }
    }

    private void collectFieldTerms(TSNode fieldDeclarationNode, StringBuilder out, String sourceCode) {
        TSNode typeNode = findChildNodeByPredicate(fieldDeclarationNode, n -> isTypeNode(n.getType()));
        if (typeNode != null) {
            appendTerms(out, getTermsFromIdentifier(getNodeText(typeNode, sourceCode)));
        }

        TSNode varDeclarator = findChildNodeByTypeRecursive(fieldDeclarationNode, "variable_declarator");
        if (varDeclarator != null) {
            TSNode nameNode = findChildNodeByType(varDeclarator, "identifier");
            if (nameNode != null) {
                appendTerms(out, getTermsFromIdentifier(getNodeText(nameNode, sourceCode)));
            }
        }
    }

    private void appendTerms(StringBuilder out, List<String> terms) {
        for (String term : terms) {
            if (term != null && !term.isBlank()) {
                out.append(term).append(' ');
            }
        }
    }

    private String cleanJavaDoc(String rawComment) {
        if (rawComment.startsWith("/*") && rawComment.endsWith("*/")) {
            rawComment = rawComment.substring(2, rawComment.length() - 2).trim();
            return Arrays.stream(rawComment.split("\r\n|\r|\n"))
                    .map(String::trim)
                    .map(line -> line.startsWith("*") ? line.substring(1).trim() : line)
                    .collect(Collectors.joining(" "));
        }
        return "";
    }

    private void extractParameters(TSNode parametersNode, Map<Biterm, Integer> freqs, String sourceCode) {
        for (int i = 0; i < parametersNode.getChildCount(); i++) {
            TSNode child = parametersNode.getChild(i);
            if ("formal_parameter".equals(child.getType())) {
                TSNode paramTypeNode = findChildNodeByPredicate(child, n -> isTypeNode(n.getType()));
                TSNode paramNameNode = findChildNodeByType(child, "identifier");

                if (paramNameNode != null && paramTypeNode != null) {
                    updateFrequencies(freqs, getTermsFromIdentifier(getNodeText(paramNameNode, sourceCode)), 1);
                    updateFrequencies(freqs, getTermsFromIdentifier(getNodeText(paramTypeNode, sourceCode)), 1);
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

    private void extractFieldInfo(TSNode fieldDeclarationNode, Map<Biterm, Integer> freqs, String sourceCode) {
        TSNode typeNode = findChildNodeByPredicate(fieldDeclarationNode, n -> isTypeNode(n.getType()));
        if (typeNode != null) {
            updateFrequencies(freqs, getTermsFromIdentifier(getNodeText(typeNode, sourceCode)), 1);
        }

        TSNode varDeclarator = findChildNodeByTypeRecursive(fieldDeclarationNode, "variable_declarator");
        if (varDeclarator != null) {
            TSNode nameNode = findChildNodeByType(varDeclarator, "identifier");
            if (nameNode != null) {
                updateFrequencies(freqs, getTermsFromIdentifier(getNodeText(nameNode, sourceCode)), 1);
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
        Deque<TSNode> queue = new ArrayDeque<>();
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

    @Override
    public ArtifactType getType() {
        return ArtifactType.JAVA_CODE;
    }

    private List<String> getTermsFromIdentifier(String identifier) {
        String processedIdentifier = TextProcessor.processIdentifier(identifier);
        return Arrays.asList(processedIdentifier.split("\\s+"));
    }

    private void updateFrequencies(Map<Biterm, Integer> freqs, List<String> terms, int weight) {
        if (terms.size() > 1) {
            for (int i = 0; i < terms.size() - 1; i++) {
                for (int j = i + 1; j < terms.size(); j++) {
                    Biterm b = new Biterm(terms.get(i), terms.get(j));
                    freqs.merge(b, weight, Integer::sum);
                }
            }
        }
    }
}

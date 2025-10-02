/* Licensed under MIT 2025. */
package io.github.ardoco.triad.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterC;

import io.github.ardoco.triad.text.TextProcessor;

public class CCodeArtifact extends Artifact {
    private static final Logger logger = LoggerFactory.getLogger(CCodeArtifact.class);

    // C-specific parser setup
    private static final TSParser parser = new TSParser();
    private static final TSLanguage cLanguage = new TreeSitterC();

    static {
        parser.setLanguage(cLanguage);
    }

    public CCodeArtifact(String identifier, String textBody) {
        super(identifier, textBody);
    }

    public CCodeArtifact(CCodeArtifact other) {
        super(other);
    }

    @Override
    public Artifact deepCopy() {
        return new CCodeArtifact(this);
    }

    @Override
    protected void preProcessing() {
        logger.info("Preprocessing C Code Artifact with Tree-sitter");
    }

    @Override
    public ArtifactType getType() {
        return ArtifactType.C_CODE;
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
            case "function_definition":
                extractFunctionInfo(node, bitermFrequencies, sourceCode);
                break;

            case "struct_specifier":
            case "union_specifier":
            case "enum_specifier":
                TSNode typeNameNode = findChildNodeByType(node, "type_identifier");
                if (typeNameNode != null) {
                    updateFrequencies(
                            bitermFrequencies, getTermsFromIdentifier(getNodeText(typeNameNode, sourceCode)), 2);
                }
                break;

            case "declaration":
                extractDeclarationInfo(node, bitermFrequencies, sourceCode);
                break;

            case "call_expression":
                TSNode functionCallNode = findChildNodeByType(node, "identifier");
                if (functionCallNode != null) {
                    updateFrequencies(
                            bitermFrequencies, getTermsFromIdentifier(getNodeText(functionCallNode, sourceCode)), 1);
                }
                break;

            case "comment":
                String commentText = getNodeText(node, sourceCode);
                if (!commentText.isEmpty()) {
                    Set<Biterm> commentBiterms = getBitermsFromText(commentText);
                    for (Biterm b : commentBiterms) {
                        bitermFrequencies.merge(b, b.getWeight(), Integer::sum);
                    }
                }
                break;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            extractElementsRecursive(node.getChild(i), bitermFrequencies, sourceCode);
        }
    }

    private void extractFunctionInfo(TSNode functionNode, Map<Biterm, Integer> freqs, String sourceCode) {
        TSNode declarator = findChildNodeByType(functionNode, "function_declarator");
        if (declarator != null) {
            TSNode functionNameNode = findChildNodeByType(declarator, "identifier");
            if (functionNameNode != null) {
                updateFrequencies(freqs, getTermsFromIdentifier(getNodeText(functionNameNode, sourceCode)), 2);
            }

            TSNode parametersNode = findChildNodeByType(declarator, "parameter_list");
            if (parametersNode != null) {
                for (int i = 0; i < parametersNode.getChildCount(); i++) {
                    TSNode paramDeclaration = parametersNode.getChild(i);
                    if ("parameter_declaration".equals(paramDeclaration.getType())) {
                        TSNode paramTypeNode =
                                findChildNodeByPredicate(paramDeclaration, n -> isCTypeNode(n.getType()));
                        TSNode paramDeclarator = findChildNodeByType(paramDeclaration, "pointer_declarator");
                        if (paramDeclarator == null) {
                            paramDeclarator = findChildNodeByType(paramDeclaration, "declarator");
                        }
                        TSNode paramIdentifier =
                                (paramDeclarator != null) ? findChildNodeByType(paramDeclarator, "identifier") : null;

                        if (paramIdentifier != null) {
                            updateFrequencies(
                                    freqs, getTermsFromIdentifier(getNodeText(paramIdentifier, sourceCode)), 1);
                        }
                        if (paramTypeNode != null) {
                            updateFrequencies(freqs, getTermsFromIdentifier(getNodeText(paramTypeNode, sourceCode)), 1);
                        }
                    }
                }
            }
        }
    }

    private void extractDeclarationInfo(TSNode declarationNode, Map<Biterm, Integer> freqs, String sourceCode) {
        TSNode typeNode = findChildNodeByPredicate(declarationNode, n -> isCTypeNode(n.getType()));
        if (typeNode != null) {
            updateFrequencies(freqs, getTermsFromIdentifier(getNodeText(typeNode, sourceCode)), 1);
        }

        for (int i = 0; i < declarationNode.getChildCount(); i++) {
            TSNode child = declarationNode.getChild(i);
            if ("init_declarator".equals(child.getType())) {
                TSNode nameNode = findChildNodeByType(child, "identifier");
                if (nameNode != null) {
                    updateFrequencies(freqs, getTermsFromIdentifier(getNodeText(nameNode, sourceCode)), 1);
                }
            }
        }
    }

    private boolean isCTypeNode(String nodeType) {
        return "primitive_type".equals(nodeType)
                || "type_identifier".equals(nodeType)
                || "sized_type_specifier".equals(nodeType);
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

package io.github.ardoco.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.github.ardoco.document.TermDocumentMatrix;

public class SimilarityMatrix {
    private Map<String, LinksList> matrix = new HashMap<>();
    private TermDocumentMatrix sourceTermMatrix;
    private TermDocumentMatrix targetTermMatrix;
    private TermDocumentMatrix sourceTermNumMatrix;
    private TermDocumentMatrix targetTermNumMatrix;

    public void addLink(String source, String target, double score) {
        if (!matrix.containsKey(source)) {
            matrix.put(source, new LinksList());
        }
        matrix.get(source).add(new SingleLink(source, target, score));
    }

    public LinksList getLinks(String source) {
        return matrix.get(source);
    }

    public Set<String> getSourceArtifacts() {
        return matrix.keySet();
    }

    public void setSourceTermMatrix(TermDocumentMatrix sourceTermMatrix) {
        this.sourceTermMatrix = sourceTermMatrix;
    }

    public void setTargetTermMatrix(TermDocumentMatrix targetTermMatrix) {
        this.targetTermMatrix = targetTermMatrix;
    }

    public void setSourceTermNumMatrix(TermDocumentMatrix termsNumOfSource) {
        this.sourceTermNumMatrix = termsNumOfSource;
    }

    public void setTargetTermNumMatrix(TermDocumentMatrix termsNumOfTarget) {
        this.targetTermNumMatrix = termsNumOfTarget;
    }
} 
/* Licensed under MIT 2025. */
package io.github.ardoco.triad.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.ardoco.triad.model.Artifact;

public class TermDocumentMatrix {
    private List<String> docNames;
    private List<String> termNames;
    private double[][] matrix;

    public TermDocumentMatrix(ArtifactsCollection artifacts) {
        // Build doc list
        this.docNames = new ArrayList<>(artifacts.keySet());
        Collections.sort(this.docNames);

        // Collect unique terms across all docs using token-based terms from text body.
        Set<String> uniqueTerms = new HashSet<>();
        Map<String, Map<String, Long>> perDocFrequencies = new HashMap<>();

        for (Map.Entry<String, Artifact> e : artifacts.entrySet()) {
            String docName = e.getKey();
            Artifact artifact = e.getValue();
            Map<String, Long> termFreqs = new HashMap<>();

            String[] terms = artifact.getTextBody().split("\\s+");
            for (String term : terms) {
                if (term == null || term.isBlank()) continue;
                uniqueTerms.add(term);
                termFreqs.merge(term, 1L, Long::sum);
            }

            perDocFrequencies.put(docName, termFreqs);
        }

        this.termNames = new ArrayList<>(uniqueTerms);
        Collections.sort(this.termNames);

        // Initialize matrix and helper index maps
        this.matrix = new double[docNames.size()][termNames.size()];

        Map<String, Integer> termIndexMap = new HashMap<>();
        for (int i = 0; i < termNames.size(); i++) {
            termIndexMap.put(termNames.get(i), i);
        }

        // Populate matrix
        for (int i = 0; i < docNames.size(); i++) {
            String docName = docNames.get(i);
            Map<String, Long> freqs = perDocFrequencies.getOrDefault(docName, Map.of());
            for (Map.Entry<String, Long> tf : freqs.entrySet()) {
                Integer termIdx = termIndexMap.get(tf.getKey());
                if (termIdx != null) {
                    matrix[i][termIdx] = tf.getValue();
                }
            }
        }
    }

    private TermDocumentMatrix(List<String> docNames, List<String> termNames, double[][] matrix) {
        this.docNames = docNames;
        this.termNames = termNames;
        this.matrix = matrix;
    }

    public int numDocs() {
        return docNames.size();
    }

    public int numTerms() {
        return termNames.size();
    }

    public double getValue(int docIndex, int termIndex) {
        return matrix[docIndex][termIndex];
    }

    public double getValue(String docName, String termName) {
        int docIndex = docNames.indexOf(docName);
        int termIndex = termNames.indexOf(termName);
        if (docIndex == -1 || termIndex == -1) {
            return 0.0;
        }
        return matrix[docIndex][termIndex];
    }

    public void setValue(int docIndex, int termIndex, double value) {
        matrix[docIndex][termIndex] = value;
    }

    public String getDocumentName(int docIndex) {
        return docNames.get(docIndex);
    }

    public String getTermName(int termIndex) {
        return termNames.get(termIndex);
    }

    public List<String> getTermNames() {
        return this.termNames;
    }

    public static List<TermDocumentMatrix> equalize(TermDocumentMatrix source, TermDocumentMatrix target) {
        Set<String> allTerms = new HashSet<>(source.termNames);
        allTerms.addAll(target.termNames);
        List<String> sortedTerms = new ArrayList<>(allTerms);
        Collections.sort(sortedTerms);

        TermDocumentMatrix newSource = createNewMatrix(source, sortedTerms);
        TermDocumentMatrix newTarget = createNewMatrix(target, sortedTerms);

        List<TermDocumentMatrix> result = new ArrayList<>();
        result.add(newSource);
        result.add(newTarget);
        return result;
    }

    private static TermDocumentMatrix createNewMatrix(TermDocumentMatrix oldMatrix, List<String> newTermNames) {
        double[][] newMatrixData = new double[oldMatrix.numDocs()][newTermNames.size()];
        Map<String, Integer> newTermIndexMap = new HashMap<>();
        for (int i = 0; i < newTermNames.size(); i++) {
            newTermIndexMap.put(newTermNames.get(i), i);
        }

        for (int i = 0; i < oldMatrix.numDocs(); i++) {
            for (int j = 0; j < oldMatrix.numTerms(); j++) {
                String termName = oldMatrix.getTermName(j);
                Integer newIndex = newTermIndexMap.get(termName);
                if (newIndex != null) {
                    newMatrixData[i][newIndex] = oldMatrix.getValue(i, j);
                }
            }
        }
        return new TermDocumentMatrix(oldMatrix.docNames, newTermNames, newMatrixData);
    }
}

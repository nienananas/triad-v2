package io.github.ardoco.ir;

import io.github.ardoco.model.Artifact;
import io.github.ardoco.model.Biterm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TermDocumentMatrix {
    private List<String> docNames;
    private List<String> termNames;
    private double[][] matrix;

    public TermDocumentMatrix(ArtifactsCollection artifacts) {
        this.docNames = new ArrayList<>(artifacts.keySet());
        Collections.sort(this.docNames);

        Set<String> uniqueTerms = new HashSet<>();
        for (Artifact artifact : artifacts.values()) {
            for (Biterm biterm : artifact.getBiterms()) {
                uniqueTerms.add(biterm.toString());
            }
        }
        this.termNames = new ArrayList<>(uniqueTerms);
        Collections.sort(this.termNames);

        this.matrix = new double[docNames.size()][termNames.size()];

        Map<String, Integer> docIndexMap = new HashMap<>();
        for (int i = 0; i < docNames.size(); i++) {
            docIndexMap.put(docNames.get(i), i);
        }

        Map<String, Integer> termIndexMap = new HashMap<>();
        for (int i = 0; i < termNames.size(); i++) {
            termIndexMap.put(termNames.get(i), i);
        }

        for (Map.Entry<String, Artifact> entry : artifacts.entrySet()) {
            String docName = entry.getKey();
            Artifact artifact = entry.getValue();
            int docIndex = docIndexMap.get(docName);

            Map<String, Long> termFrequencies = new HashMap<>();
            for (Biterm biterm : artifact.getBiterms()) {
                termFrequencies.merge(biterm.toString(), (long) biterm.getWeight(), Long::sum);
            }

            for (Map.Entry<String, Long> termEntry : termFrequencies.entrySet()) {
                String termName = termEntry.getKey();
                int termIndex = termIndexMap.get(termName);
                matrix[docIndex][termIndex] = termEntry.getValue();
            }
        }
    }

    private TermDocumentMatrix(List<String> docNames, List<String> termNames, double[][] matrix) {
        this.docNames = docNames;
        this.termNames = termNames;
        this.matrix = matrix;
    }

    public int NumDocs() {
        return docNames.size();
    }

    public int NumTerms() {
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

    public static List<TermDocumentMatrix> Equalize(TermDocumentMatrix source, TermDocumentMatrix target) {
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
        double[][] newMatrixData = new double[oldMatrix.NumDocs()][newTermNames.size()];
        Map<String, Integer> newTermIndexMap = new HashMap<>();
        for (int i = 0; i < newTermNames.size(); i++) {
            newTermIndexMap.put(newTermNames.get(i), i);
        }

        for (int i = 0; i < oldMatrix.NumDocs(); i++) {
            for (int j = 0; j < oldMatrix.NumTerms(); j++) {
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
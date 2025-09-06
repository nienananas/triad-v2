package io.github.ardoco.triad.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SimilarityMatrix {
    private Map<String, LinksList> matrix = new HashMap<>();

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

    public Set<String> getTargetArtifacts() {
        Set<String> targetArtifacts = new HashSet<>();
        for (LinksList links : matrix.values()) {
            for (SingleLink link : links) {
                targetArtifacts.add(link.getTargetArtifactId());
            }
        }
        return targetArtifacts;
    }

    public List<SingleLink> getAllLinks() {
        List<SingleLink> allLinks = new ArrayList<>();
        for (LinksList linksList : matrix.values()) {
            allLinks.addAll(linksList);
        }
        return allLinks;
    }

    public double getScore(String source, String target) {
        if (matrix.containsKey(source)) {
            for (SingleLink link : matrix.get(source)) {
                if (link.getTargetArtifactId().equals(target)) {
                    return link.getScore();
                }
            }
        }
        return 0.0;
    }

    public void setScore(String source, String target, double newScore) {
        if (!matrix.containsKey(source)) {
            matrix.put(source, new LinksList());
        }

        LinksList links = matrix.get(source);
        links.removeIf(link -> link.getTargetArtifactId().equals(target));
        links.add(new SingleLink(source, target, newScore));
    }

    public SimilarityMatrix deepCopy() {
        SimilarityMatrix newMatrix = new SimilarityMatrix();
        for (Map.Entry<String, LinksList> entry : this.matrix.entrySet()) {
            String source = entry.getKey();
            LinksList copiedLinks = new LinksList();
            for (SingleLink link : entry.getValue()) {
                copiedLinks.add(link);
            }
            newMatrix.matrix.put(source, copiedLinks);
        }
        return newMatrix;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Set<String> targetArtifacts = getTargetArtifacts();
        if (targetArtifacts.isEmpty() || getSourceArtifacts().isEmpty()) {
            return "";
        }

        List<String> sortedTargets = new ArrayList<>(targetArtifacts);
        Collections.sort(sortedTargets);

        sb.append("Source Artifact,");
        sb.append(String.join(",", sortedTargets));
        sb.append("\n");

        List<String> sortedSources = new ArrayList<>(getSourceArtifacts());
        Collections.sort(sortedSources);

        for (String source : sortedSources) {
            sb.append(source).append(",");
            LinksList links = matrix.get(source);
            Map<String, Double> targetScores = new HashMap<>();
            if (links != null) {
                for (SingleLink link : links) {
                    targetScores.put(link.getTargetArtifactId(), link.getScore());
                }
            }

            List<String> scores = new ArrayList<>();
            for (String target : sortedTargets) {
                double score = targetScores.getOrDefault(target, 0.0);
                scores.add(String.format("%.4f", score));
            }
            sb.append(String.join(",", scores));
            sb.append("\n");
        }

        return sb.toString();
    }
}
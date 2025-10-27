/* Licensed under MIT 2025. */
package io.github.ardoco.triad.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SimilarityMatrix {
    private Map<String, LinksList> matrix = new HashMap<>();

    /**
     * Add a similarity score for a source-target pair.
     */
    public void addLink(String source, String target, double score) {
        if (!matrix.containsKey(source)) {
            matrix.put(source, new LinksList());
        }
        matrix.get(source).add(new SingleLink(source, target, score));
    }

    /**
     * Get all links originating from a given source artifact id.
     */
    public LinksList getLinks(String source) {
        return matrix.get(source);
    }

    /**
     * Return the set of source artifact identifiers appearing in the matrix.
     */
    public Set<String> getSourceArtifacts() {
        return matrix.keySet();
    }

    /**
     * Return the set of target artifact identifiers appearing in the matrix.
     */
    public Set<String> getTargetArtifacts() {
        Set<String> targetArtifacts = new HashSet<>();
        for (LinksList links : matrix.values()) {
            for (SingleLink link : links) {
                targetArtifacts.add(link.getTargetArtifactId());
            }
        }
        return targetArtifacts;
    }

    /**
     * Flatten and return all links contained in the matrix.
     */
    public List<SingleLink> getAllLinks() {
        List<SingleLink> allLinks = new ArrayList<>();
        for (LinksList linksList : matrix.values()) {
            allLinks.addAll(linksList);
        }
        return allLinks;
    }

    /**
     * Flatten and return all links above a certain threshold.
     *
     * @param threshold the threshold that determines which links are returned
     * @author ninananas
     */
    public List<SingleLink> getLinksAboveThreshold(double threshold) {
        return this.getAllLinks().stream().filter(link -> link.getScore() > threshold).collect(Collectors.toList());
    }

    /**
     * Flatten and return all links below a certain threshold.
     *
     * @param threshold the threshold that determines which links are returned
     * @author ninananas
     */
    public List<SingleLink> getLinksBelowThreshold(double threshold) {
        return this.getAllLinks().stream().filter(link -> link.getScore() < threshold).collect(Collectors.toList());
    }


    /**
     * Flatten and get the links with the top-k highest similarity score for each source artifact.
     * If less than k links exist for a source artifact, all existing links for this artifact are returned.
     *
     * @param k the number of links that should be retrieved
     * @author ninananas
     */
    public List<SingleLink> getTopKLinks(int k) {
        List<SingleLink> allLinks = new ArrayList<>();
        for (LinksList linksList : matrix.values()) {
            linksList.sort(Comparator.comparingDouble(SingleLink::getScore));
            int numLinks = linksList.size();
            if (k >= numLinks) {
                allLinks.addAll(linksList);
            } else {
                allLinks.addAll(linksList.subList(numLinks - k, numLinks));
            }
        }
        return allLinks;
    }

    /**
     * Get the similarity score for a specific source-target pair (0.0 if absent).
     */
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

    /**
     * Set or replace the similarity score for a source-target pair.
     */
    public void setScore(String source, String target, double newScore) {
        if (!matrix.containsKey(source)) {
            matrix.put(source, new LinksList());
        }

        LinksList links = matrix.get(source);
        links.removeIf(link -> link.getTargetArtifactId().equals(target));
        links.add(new SingleLink(source, target, newScore));
    }

    /**
     * Create a deep copy of this similarity matrix.
     */
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

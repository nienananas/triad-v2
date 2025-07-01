package io.github.ardoco.model;

public class SingleLink implements Comparable<SingleLink> {
    private final String sourceArtifactId;
    private final String targetArtifactId;
    private final double score;

    public SingleLink(String sourceArtifactId, String targetArtifactId, double score) {
        this.sourceArtifactId = sourceArtifactId;
        this.targetArtifactId = targetArtifactId;
        this.score = score;
    }

    public String getSourceArtifactId() {
        return sourceArtifactId;
    }

    public String getTargetArtifactId() {
        return targetArtifactId;
    }

    public double getScore() {
        return score;
    }

    @Override
    public int compareTo(SingleLink other) {
        return Double.compare(this.score, other.score);
    }
} 
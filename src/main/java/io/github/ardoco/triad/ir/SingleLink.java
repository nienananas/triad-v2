package io.github.ardoco.triad.ir;

public class SingleLink implements Comparable<SingleLink> {
    private final String sourceArtifactId;
    private final String targetArtifactId;
    private final double score;

    /**
     * Create a link tuple between a source and target with an associated score.
     */
    public SingleLink(String sourceArtifactId, String targetArtifactId, double score) {
        this.sourceArtifactId = sourceArtifactId;
        this.targetArtifactId = targetArtifactId;
        this.score = score;
    }

    /**
     * @return the source artifact identifier
     */
    public String getSourceArtifactId() {
        return sourceArtifactId;
    }

    /**
     * @return the target artifact identifier
     */
    public String getTargetArtifactId() {
        return targetArtifactId;
    }

    /**
     * @return the similarity score of this link
     */
    public double getScore() {
        return score;
    }

    @Override
    public int compareTo(SingleLink other) {
        return Double.compare(this.score, other.score);
    }
}
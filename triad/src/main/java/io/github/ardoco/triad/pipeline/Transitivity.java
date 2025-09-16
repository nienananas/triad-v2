package io.github.ardoco.triad.pipeline;

import io.github.ardoco.triad.ir.LinksList;
import io.github.ardoco.triad.ir.SimilarityMatrix;
import io.github.ardoco.triad.ir.SingleLink;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class Transitivity {

    private final SimilarityMatrix sourceIntermediateSimilarity;
    private final SimilarityMatrix intermediateTargetSimilarity;
    private final SimilarityMatrix sourceSourceSimilarity;
    private final SimilarityMatrix intermediateIntermediateSimilarity;

    // --- Tunable Parameters ---
    private static final int T_HOP1 = 3;
    private static final double M_HOP1 = 0.5;

    public Transitivity(SimilarityMatrix sourceIntermediateSimilarity,
                       SimilarityMatrix intermediateTargetSimilarity,
                       SimilarityMatrix sourceSourceSimilarity,
                       SimilarityMatrix intermediateIntermediateSimilarity) {
        this.sourceIntermediateSimilarity = sourceIntermediateSimilarity;
        this.intermediateTargetSimilarity = intermediateTargetSimilarity;
        this.sourceSourceSimilarity = sourceSourceSimilarity;
        this.intermediateIntermediateSimilarity = intermediateIntermediateSimilarity;
    }

    public SimilarityMatrix applyTransitivity(SimilarityMatrix baseMatrix) {
        SimilarityMatrix adjustedMatrix = baseMatrix.deepCopy();
        
        // Get all possible sources and targets from the similarity matrices
        Set<String> allSources = this.sourceIntermediateSimilarity.getSourceArtifacts();
        Set<String> allTargets = this.intermediateTargetSimilarity.getTargetArtifacts();

        for (String source : allSources) {
            for (String target : allTargets) {
                double currentScore = baseMatrix.getScore(source, target);
                double transitiveScore = calculateBestTransitivePath(source, target);

                // Use conservative fusion: take maximum of current and transitive scores
                // This ensures we don't degrade existing good scores
                if (transitiveScore > 0) {
                    double newScore = Math.max(currentScore, transitiveScore);
                    adjustedMatrix.setScore(source, target, newScore);
                }
            }
        }
        return adjustedMatrix;
    }

    /**
     * Calculate the best transitive path score from source to target.
     * Uses the simple and reliable outer transitivity: source -> intermediate -> target.
     */
    private double calculateBestTransitivePath(String source, String target) {
        double bestScore = 0.0;

        // Simple outer transitivity: s -> m -> t
        List<SingleLink> sourceToIntermediate = getTopLinks(this.sourceIntermediateSimilarity, source, T_HOP1, M_HOP1);
        
        for (SingleLink smLink : sourceToIntermediate) {
            String intermediate = smLink.getTargetArtifactId();
            List<SingleLink> intermediateToTarget = getTopLinks(this.intermediateTargetSimilarity, intermediate, T_HOP1, M_HOP1);
            
            for (SingleLink mtLink : intermediateToTarget) {
                if (mtLink.getTargetArtifactId().equals(target)) {
                    // Conservative transitive score calculation
                    double transitiveScore = smLink.getScore() * mtLink.getScore();
                    bestScore = Math.max(bestScore, transitiveScore);
                }
            }
        }
        
        return bestScore;
    }

    private List<SingleLink> getTopLinks(SimilarityMatrix matrix, String sourceId, int topK, double m) {
        LinksList links = matrix.getLinks(sourceId);
        if (links == null || links.isEmpty()) return List.of();
        double maxScore = links.stream().mapToDouble(SingleLink::getScore).max().orElse(0.0);
        if (maxScore == 0.0) return List.of();
        double threshold = maxScore * m;
        return links.stream()
                .filter(link -> link.getScore() >= threshold)
                .sorted(Comparator.comparingDouble(SingleLink::getScore).reversed())
                .limit(topK)
                .toList();
    }
}

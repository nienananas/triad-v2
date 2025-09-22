/* Licensed under MIT 2025. */
package io.github.ardoco.triad.pipeline;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import io.github.ardoco.triad.ir.LinksList;
import io.github.ardoco.triad.ir.SimilarityMatrix;
import io.github.ardoco.triad.ir.SingleLink;

public class Transitivity {

    private final SimilarityMatrix sourceIntermediateSimilarity;
    private final SimilarityMatrix intermediateTargetSimilarity;
    private final SimilarityMatrix sourceSourceSimilarity;
    private final SimilarityMatrix intermediateIntermediateSimilarity;

    // --- Tunable parameters ---
    private static final int T_HOP1 = 3; // top-k per hop
    private static final double M_HOP1 = 0.5; // relative threshold per hop

    public Transitivity(
            SimilarityMatrix sourceIntermediateSimilarity,
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
     * Implements original-style multi-hop transitivity:
     * 1) Outer: s -> m -> t
     * 2) Inner (source): s -> s' -> m -> t
     * 3) Inner (intermediate): s -> m -> m' -> t
     */
    private double calculateBestTransitivePath(String source, String target) {
        double bestScore = 0.0;

        // 1) Outer: s -> m -> t
        List<SingleLink> s_to_m = getTopLinks(this.sourceIntermediateSimilarity, source, T_HOP1, M_HOP1);
        for (SingleLink sm : s_to_m) {
            String m = sm.getTargetArtifactId();
            List<SingleLink> m_to_t = getTopLinks(this.intermediateTargetSimilarity, m, T_HOP1, M_HOP1);
            for (SingleLink mt : m_to_t) {
                if (mt.getTargetArtifactId().equals(target)) {
                    double score = sm.getScore() * mt.getScore();
                    bestScore = Math.max(bestScore, score);
                }
            }
        }

        // 2) Inner (source): s -> s' -> m -> t
        List<SingleLink> s_to_sprime = getTopLinks(this.sourceSourceSimilarity, source, T_HOP1, M_HOP1);
        for (SingleLink ss : s_to_sprime) {
            String sprime = ss.getTargetArtifactId();
            List<SingleLink> sprime_to_m = getTopLinks(this.sourceIntermediateSimilarity, sprime, T_HOP1, M_HOP1);
            for (SingleLink sPm : sprime_to_m) {
                String m = sPm.getTargetArtifactId();
                List<SingleLink> m_to_t = getTopLinks(this.intermediateTargetSimilarity, m, T_HOP1, M_HOP1);
                for (SingleLink mt : m_to_t) {
                    if (mt.getTargetArtifactId().equals(target)) {
                        double score = ss.getScore() * sPm.getScore() * mt.getScore();
                        bestScore = Math.max(bestScore, score);
                    }
                }
            }
        }

        // 3) Inner (intermediate): s -> m -> m' -> t
        for (SingleLink sm : s_to_m) {
            String m = sm.getTargetArtifactId();
            List<SingleLink> m_to_mprime = getTopLinks(this.intermediateIntermediateSimilarity, m, T_HOP1, M_HOP1);
            for (SingleLink mMp : m_to_mprime) {
                String mprime = mMp.getTargetArtifactId();
                List<SingleLink> mprime_to_t = getTopLinks(this.intermediateTargetSimilarity, mprime, T_HOP1, M_HOP1);
                for (SingleLink mpt : mprime_to_t) {
                    if (mpt.getTargetArtifactId().equals(target)) {
                        double score = sm.getScore() * mMp.getScore() * mpt.getScore();
                        bestScore = Math.max(bestScore, score);
                    }
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

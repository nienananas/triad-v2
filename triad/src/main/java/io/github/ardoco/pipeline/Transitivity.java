package io.github.ardoco.pipeline;

import io.github.ardoco.ir.LinksList;
import io.github.ardoco.ir.SimilarityMatrix;
import io.github.ardoco.ir.SingleLink;

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
    private static final int T_HOP2 = T_HOP1 - 1; // 2
    private static final int T_HOP3 = T_HOP1 - 2; // 1

    private static final double M_HOP1 = 0.5;
    private static final double M_HOP2 = M_HOP1 + 0.1; // 0.6
    private static final double M_HOP3 = M_HOP1 + 0.2; // 0.7

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
        Set<String> allSources = baseMatrix.getSourceArtifacts();
        Set<String> allTargets = baseMatrix.getTargetArtifacts();

        for (String source : allSources) {
            for (String target : allTargets) {
                double currentScore = baseMatrix.getScore(source, target);
                double bonus = calculateTransitiveBonus(source, target);

                if (bonus > 0) {
                    double boosted = currentScore * (1.0 + bonus);
                    double newScore = Math.min(Math.max(currentScore, boosted), 0.9999);
                    adjustedMatrix.setScore(source, target, newScore);
                }
            }
        }
        return adjustedMatrix;
    }

    private double calculateTransitiveBonus(String source, String target) {
        double bonus = 0.0;

        // --- Outer Transitivity: s -> m -> t ---
        List<SingleLink> s_m_links = getTopLinks(this.sourceIntermediateSimilarity, source, T_HOP1, M_HOP1);
        for (SingleLink smLink : s_m_links) {
            String middle = smLink.getTargetArtifactId();
            List<SingleLink> m_t_links = getTopLinks(this.intermediateTargetSimilarity, middle, T_HOP2, M_HOP2);
            for (SingleLink mtLink : m_t_links) {
                if (mtLink.getTargetArtifactId().equals(target)) {
                    bonus += smLink.getScore() * mtLink.getScore();
                }
            }
        }

        // --- Inner Transitivity (Source-side): s -> s' -> m -> t ---
        List<SingleLink> s_s_links = getTopLinks(this.sourceSourceSimilarity, source, T_HOP1, M_HOP1);
        for (SingleLink ssLink : s_s_links) {
            String sPrime = ssLink.getTargetArtifactId();
            if (sPrime.equals(source)) continue;

            List<SingleLink> sp_m_links = getTopLinks(this.sourceIntermediateSimilarity, sPrime, T_HOP2, M_HOP2);
            for (SingleLink spmLink : sp_m_links) {
                String middle = spmLink.getTargetArtifactId();
                List<SingleLink> m_t_links = getTopLinks(this.intermediateTargetSimilarity, middle, T_HOP3, M_HOP3);
                 for (SingleLink mtLink : m_t_links) {
                    if (mtLink.getTargetArtifactId().equals(target)) {
                       bonus += ssLink.getScore() * spmLink.getScore() * mtLink.getScore();
                    }
                }
            }
        }

        // --- Inner Transitivity (Middle-side): s -> m -> m' -> t ---
        for (SingleLink smLink : s_m_links) {
            String middle = smLink.getTargetArtifactId();
            List<SingleLink> m_m_links = getTopLinks(this.intermediateIntermediateSimilarity, middle, T_HOP2, M_HOP2);
            for (SingleLink mmLink : m_m_links) {
                String mPrime = mmLink.getTargetArtifactId();
                if (mPrime.equals(middle)) continue;

                List<SingleLink> mp_t_links = getTopLinks(this.intermediateTargetSimilarity, mPrime, T_HOP3, M_HOP3);
                for (SingleLink mptLink : mp_t_links) {
                   if (mptLink.getTargetArtifactId().equals(target)) {
                       bonus += smLink.getScore() * mmLink.getScore() * mptLink.getScore();
                   }
                }
            }
        }
        return bonus;
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
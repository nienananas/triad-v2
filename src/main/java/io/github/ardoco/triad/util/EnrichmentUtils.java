/* Licensed under MIT 2025. */
package io.github.ardoco.triad.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ardoco.triad.ir.ArtifactsCollection;
import io.github.ardoco.triad.ir.LinksList;
import io.github.ardoco.triad.ir.SimilarityMatrix;
import io.github.ardoco.triad.ir.SingleLink;
import io.github.ardoco.triad.model.Artifact;
import io.github.ardoco.triad.model.ArtifactFactory;
import io.github.ardoco.triad.model.Biterm;

/**
 * Utility class containing shared logic for the enrichment process.
 */
public final class EnrichmentUtils {
    private static final Logger logger = LoggerFactory.getLogger(EnrichmentUtils.class);

    private EnrichmentUtils() {}

    private static double propDouble(String key, double defVal) {
        try {
            return Double.parseDouble(System.getProperty(key, Double.toString(defVal)));
        } catch (Exception e) {
            return defVal;
        }
    }

    private static int propInt(String key, int defVal) {
        try {
            return Integer.parseInt(System.getProperty(key, Integer.toString(defVal)));
        } catch (Exception e) {
            return defVal;
        }
    }

    // Defaults mimic original TRIAD behavior; can be overridden via -D properties
    private static final double M_ENRICH = propDouble("triad.enrich.m", 0.5);
    private static final int TOP_K_ENRICH = propInt("triad.enrich.topk", 3);
    private static final int MAX_REP_PER_BITERM = propInt("triad.enrich.maxrep", 4);
    private static final int MIN_AGREEMENTS = propInt("triad.enrich.minAgree", 2);
    private static final int MAX_BITERMS_PER_DOC = propInt("triad.enrich.maxBiterms", 24);
    private static final boolean DEBUG_ENRICH = Boolean.parseBoolean(System.getProperty("triad.debug.enrich", "false"));

    /**
     * Build biterm frequency maps per artifact.
     *
     * @param artifacts artifacts whose biterms should be counted
     * @return map: artifactId -> ("term1 term2" -> frequency)
     */
    public static Map<String, Map<String, Integer>> getBitermFrequencyMap(Set<Artifact> artifacts) {
        Map<String, Map<String, Integer>> artifactBitermMap = new HashMap<>();
        for (Artifact artifact : artifacts) {
            Map<String, Integer> freqMap = new HashMap<>();
            Set<Biterm> biterms = artifact.getBiterms();
            for (Biterm biterm : biterms) {
                String term1 = biterm.getFirstTerm();
                String term2 = biterm.getSecondTerm();
                if (term1 == null || term1.isEmpty() || term2 == null || term2.isEmpty()) {
                    continue;
                }
                String key = term1.toLowerCase(Locale.ROOT) + " " + term2.toLowerCase(Locale.ROOT);
                freqMap.merge(key, biterm.getWeight(), Integer::sum);
            }
            if (DEBUG_ENRICH) {
                logger.debug("[ENRICH-DBG] bitermFreq {} size={}", artifact.getIdentifier(), freqMap.size());
            }
            artifactBitermMap.put(artifact.getIdentifier(), freqMap);
        }
        return artifactBitermMap;
    }

    /**
     * Select consensual biterms via weighted voting from nearest neighbors.
     *
     * @param artifacts artifacts to enrich (rows in rowSimMatrix)
     * @param neighborBitermMap neighborId -> biterm frequencies
     * @param rowSimMatrix similarity matrix used to pick neighbors and weights
     * @return artifactId -> (biterm -> weighted score)
     */
    public static Map<String, Map<String, Double>> selectNeighborConsensualBiterms(
            Set<Artifact> artifacts,
            Map<String, Map<String, Integer>> neighborBitermMap,
            SimilarityMatrix rowSimMatrix) {
        Map<String, Map<String, Double>> out = new HashMap<>();
        for (Artifact a : artifacts) {
            String aid = a.getIdentifier();
            LinksList neighbors = rowSimMatrix.getLinks(aid);
            Map<String, Double> counts = new HashMap<>();
            if (neighbors != null && !neighbors.isEmpty()) {
                double rowMax = neighbors.stream()
                        .mapToDouble(SingleLink::getScore)
                        .max()
                        .orElse(0.0);
                double thr = rowMax * M_ENRICH;
                List<SingleLink> top = neighbors.stream()
                        .filter(l -> l.getScore() >= thr)
                        .sorted(Comparator.comparingDouble(SingleLink::getScore).reversed())
                        .limit(TOP_K_ENRICH)
                        .toList();

                if (DEBUG_ENRICH) {
                    logger.debug(
                            "[ENRICH-DBG] {} rowMax={} thr={} topK={} of {}",
                            aid,
                            String.format("%.4f", rowMax),
                            String.format("%.4f", thr),
                            top.size(),
                            neighbors.size());
                }

                for (SingleLink n : top) {
                    Map<String, Integer> nb = neighborBitermMap.getOrDefault(n.getTargetArtifactId(), Map.of());
                    double vote = (rowMax > 0.0) ? (n.getScore() / rowMax) : 0.0;
                    for (Map.Entry<String, Integer> e : nb.entrySet()) {
                        String b = e.getKey();
                        int freq = e.getValue();
                        double weight = vote * Math.log(1.0 + freq);
                        counts.merge(b, weight, Double::sum);
                    }
                }
                if (DEBUG_ENRICH) {
                    int candidateCount = counts.size();
                    long passMinAgree = counts.values().stream()
                            .filter(v -> v >= (double) MIN_AGREEMENTS - 1e-9)
                            .count();
                    logger.debug(
                            "[ENRICH-DBG] {} candidate_biterms={} pass_minAgree={} (minAgree={})",
                            aid,
                            candidateCount,
                            passMinAgree,
                            MIN_AGREEMENTS);
                }
            }
            out.put(aid, counts);
        }
        return out;
    }

    /**
     * Create an extended artifacts collection by appending consensual biterms to texts.
     *
     * @param originals base artifacts to extend
     * @param bitermScores artifactId -> (biterm -> score)
     * @param tagForLogs label used in enrichment logging
     * @return extended collection keyed by artifact identifiers
     */
    public static ArtifactsCollection createExtendedCollection(
            Set<Artifact> originals, Map<String, Map<String, Double>> bitermScores, String tagForLogs) {
        ArtifactsCollection col = new ArtifactsCollection();
        long totalAppended = 0;
        long totalBitermsKept = 0;

        for (Artifact orig : originals) {
            String base = orig.getEnrichmentBaseText();
            StringBuilder sb = new StringBuilder(base).append('\n');

            Map<String, Double> scoreMap = bitermScores.getOrDefault(orig.getIdentifier(), Map.of());

            List<Map.Entry<String, Double>> topBiterms = scoreMap.entrySet().stream()
                    .filter(e -> e.getValue() >= (double) MIN_AGREEMENTS - 1e-9)
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(MAX_BITERMS_PER_DOC)
                    .collect(Collectors.toList());

            int appendedThis = 0;
            int keptThis = 0;
            for (Map.Entry<String, Double> e : topBiterms) {
                String biterm = e.getKey();
                int reps = Math.min((int) Math.round(e.getValue()), MAX_REP_PER_BITERM);
                if (reps <= 0) continue;
                String[] two = normalizeBiterm(biterm);
                if (two == null) {
                    if (DEBUG_ENRICH) {
                        logger.debug("[ENRICH-DBG] Dropped biterm '{}' due to normalization", biterm);
                    }
                    continue;
                }
                keptThis++;
                for (int i = 0; i < reps; i++) {
                    sb.append(two[0]).append(' ').append(two[1]).append(' ');
                    appendedThis += 2;
                }
            }
            if (DEBUG_ENRICH && keptThis == 0 && !scoreMap.isEmpty()) {
                logger.debug(
                        "[ENRICH-DBG] No biterms kept for {} (minAgree={}, candidates={})",
                        orig.getIdentifier(),
                        MIN_AGREEMENTS,
                        scoreMap.size());
            }
            totalAppended += appendedThis;
            totalBitermsKept += keptThis;

            col.put(orig.getIdentifier(), ArtifactFactory.create(orig.getIdentifier(), sb.toString(), orig.getType()));
        }

        int n = originals.size();
        logger.info(
                "[ENRICH] {} avg_kept_biterms={}  avg_appended_terms={}",
                tagForLogs,
                (n == 0 ? 0 : String.format("%.2f", totalBitermsKept * 1.0 / n)),
                (n == 0 ? 0 : String.format("%.2f", totalAppended * 1.0 / n)));
        return col;
    }

    /**
     * Element-wise max fusion of two similarity matrices (safeguard against degradation).
     */
    public static SimilarityMatrix elementwiseMax(SimilarityMatrix a, SimilarityMatrix b) {
        SimilarityMatrix out = new SimilarityMatrix();
        for (String s : a.getSourceArtifacts()) {
            for (String t : a.getTargetArtifacts()) {
                double v = Math.max(a.getScore(s, t), b.getScore(s, t));
                if (v > 0.0) out.addLink(s, t, v);
            }
        }
        return out;
    }

    private static String[] normalizeBiterm(String raw) {
        List<String> tokens = new ArrayList<>();
        Matcher m = Pattern.compile("[A-Za-z]+").matcher(raw);
        while (m.find()) tokens.add(m.group().toLowerCase(Locale.ROOT));
        if (tokens.size() < 2) return null;
        return new String[] {tokens.get(0), tokens.get(1)};
    }

    /**
     * Log concise enrichment statistics for a subset of artifacts.
     */
    public static void debugEnrichmentStats(
            String tag, Set<Artifact> arts, Map<String, Map<String, Double>> bitermMap) {
        int shown = 0;
        for (Artifact a : arts) {
            var m = bitermMap.getOrDefault(a.getIdentifier(), Map.of());
            double added = m.values().stream().mapToDouble(Double::doubleValue).sum();
            if (shown < 5 && added > 0) {
                logger.info("[ENRICH] {} {} consensual_biterms={}", tag, a.getIdentifier(), Math.round(added));
                shown++;
            }
        }
    }

    /**
     * Fuses two similarity matrices by taking the element-wise average of their scores.
     * This method mirrors the fusion strategy of the original TRIAD implementation.
     *
     * @param a The first similarity matrix.
     * @param b The second similarity matrix.
     * @return A new similarity matrix containing the averaged scores.
     */
    /**
     * Element-wise average fusion of two similarity matrices.
     */
    public static SimilarityMatrix elementwiseAverage(SimilarityMatrix a, SimilarityMatrix b) {
        SimilarityMatrix fused = new SimilarityMatrix();
        Set<String> allSources = new HashSet<>(a.getSourceArtifacts());
        allSources.addAll(b.getSourceArtifacts());

        Set<String> allTargets = new HashSet<>(a.getTargetArtifacts());
        allTargets.addAll(b.getTargetArtifacts());

        for (String s : allSources) {
            for (String t : allTargets) {
                double scoreA = a.getScore(s, t);
                double scoreB = b.getScore(s, t);
                double avgScore = (scoreA + scoreB) / 2.0;

                if (avgScore > 0) {
                    fused.addLink(s, t, avgScore);
                }
            }
        }
        return fused;
    }
}

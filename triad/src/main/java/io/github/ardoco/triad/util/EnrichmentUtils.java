package io.github.ardoco.triad.util;

import io.github.ardoco.triad.ir.ArtifactsCollection;
import io.github.ardoco.triad.ir.LinksList;
import io.github.ardoco.triad.ir.SimilarityMatrix;
import io.github.ardoco.triad.ir.SingleLink;
import io.github.ardoco.triad.model.Artifact;
import io.github.ardoco.triad.model.ArtifactFactory;
import io.github.ardoco.triad.model.Biterm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class containing shared logic for the enrichment process.
 */
public final class EnrichmentUtils {
    private static final Logger logger = LoggerFactory.getLogger(EnrichmentUtils.class);

    private EnrichmentUtils() {}

    private static double propDouble(String key, double defVal) {
        try { return Double.parseDouble(System.getProperty(key, Double.toString(defVal))); }
        catch (Exception e) { return defVal; }
    }
    private static int propInt(String key, int defVal) {
        try { return Integer.parseInt(System.getProperty(key, Integer.toString(defVal))); }
        catch (Exception e) { return defVal; }
    }

    private static final double M_ENRICH = propDouble("triad.enrich.m", 0.01);  // Extremely aggressive threshold  
    private static final int TOP_K_ENRICH = propInt("triad.enrich.topk", 100); // Maximum neighbors
    private static final int MAX_REP_PER_BITERM = propInt("triad.enrich.maxrep", 999);
    private static final int MIN_AGREEMENTS = propInt("triad.enrich.minAgree", 1);
    private static final int MAX_BITERMS_PER_DOC = propInt("triad.enrich.maxBiterms", 100000);

    public static Map<String, Map<String, Integer>> getBitermFrequencyMap(Set<Artifact> artifacts) {
        Map<String, Map<String, Integer>> artifactBitermMap = new HashMap<>();
        for (Artifact artifact : artifacts) {
            Map<String, Integer> freqMap = new HashMap<>();
            Set<Biterm> biterms = artifact.getBiterms();
            for (Biterm biterm : biterms) {
                // Use the individual terms directly instead of toString() to avoid normalization issues
                String term1 = biterm.getFirstTerm().toLowerCase();
                String term2 = biterm.getSecondTerm().toLowerCase();
                String key = term1 + " " + term2;
                freqMap.merge(key, biterm.getWeight(), Integer::sum);
            }
            artifactBitermMap.put(artifact.getIdentifier(), freqMap);
        }
        return artifactBitermMap;
    }

    public static Map<String, Map<String, Integer>> selectNeighborConsensualBiterms(Set<Artifact> artifacts,
                                                                                   Map<String, Map<String, Integer>> neighborBitermMap,
                                                                                   SimilarityMatrix rowSimMatrix) {
        Map<String, Map<String, Integer>> out = new HashMap<>();
        for (Artifact a : artifacts) {
            String aid = a.getIdentifier();
            LinksList neighbors = rowSimMatrix.getLinks(aid);
            Map<String, Integer> counts = new HashMap<>();
            if (neighbors != null && !neighbors.isEmpty()) {
                double rowMax = neighbors.stream().mapToDouble(SingleLink::getScore).max().orElse(0.0);
                double thr = rowMax * M_ENRICH;
                List<SingleLink> top = neighbors.stream()
                        .filter(l -> l.getScore() >= thr)
                        .sorted(Comparator.comparingDouble(SingleLink::getScore).reversed())
                        .limit(TOP_K_ENRICH)
                        .toList();

                // Match original implementation: add ALL biterms from each neighbor with frequency 1
                for (SingleLink n : top) {
                    Map<String, Integer> nb = neighborBitermMap.getOrDefault(n.getTargetArtifactId(), Map.of());
                    for (String bitermKey : nb.keySet()) {
                        // Original adds each biterm once per neighbor (not accumulated frequencies)
                        counts.merge(bitermKey, 1, Integer::sum);
                    }
                }
            }
            out.put(aid, counts);
        }
        return out;
    }

    public static ArtifactsCollection createExtendedCollection(Set<Artifact> originals,
                                                               Map<String, Map<String, Integer>> bitermFrequencies,
                                                               String tagForLogs) {
        ArtifactsCollection col = new ArtifactsCollection();
        long totalAppended = 0;
        long totalBitermsKept = 0;

        for (Artifact orig : originals) {
            String base = orig.getTextBody();
            StringBuilder sb = new StringBuilder(base).append('\n');

            Map<String, Integer> freqMap = bitermFrequencies.getOrDefault(orig.getIdentifier(), Map.of());
            
            List<Map.Entry<String, Integer>> topBiterms = freqMap.entrySet().stream()
                    .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(MAX_BITERMS_PER_DOC)
                    .collect(Collectors.toList());

            int appendedThis = 0;
            int keptThis = 0;
            for (Map.Entry<String, Integer> e : topBiterms) {
                String biterm = e.getKey();
                int reps = Math.min(e.getValue(), MAX_REP_PER_BITERM);
                if (reps <= 0) continue;
                String[] two = normalizeBiterm(biterm);
                if (two == null) continue;
                keptThis++;
                for (int i = 0; i < reps; i++) {
                    sb.append(two[0]).append(' ').append(two[1]).append(' ');
                    appendedThis += 2;
                }
            }
            totalAppended += appendedThis;
            totalBitermsKept += keptThis;

            col.put(orig.getIdentifier(), ArtifactFactory.create(orig.getIdentifier(), sb.toString(), orig.getType()));
        }

        int n = originals.size();
        logger.info("[ENRICH] {} avg_kept_biterms={}  avg_appended_terms={}", tagForLogs,
                (n == 0 ? 0 : String.format("%.2f", totalBitermsKept * 1.0 / n)),
                (n == 0 ? 0 : String.format("%.2f", totalAppended * 1.0 / n)));
        return col;
    }

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
        return new String[]{tokens.get(0), tokens.get(1)};
    }

    public static void debugEnrichmentStats(String tag, Set<Artifact> arts, Map<String, Map<String, Integer>> bitermMap) {
        int shown = 0;
        for (Artifact a : arts) {
            var m = bitermMap.getOrDefault(a.getIdentifier(), Map.of());
            long added = m.values().stream().mapToLong(Integer::intValue).sum();
            if (shown < 5 && added > 0) {
                logger.info("[ENRICH] {} {} consensual_biterms={}", tag, a.getIdentifier(), added);
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
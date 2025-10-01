/* Licensed under MIT 2025. */
package io.github.ardoco.triad.model;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ardoco.triad.config.Config;
import io.github.ardoco.triad.config.ProjectConfig;

/**
 * Compares biterms between the standard Dronology project and the preprocessed
 * Dronology-Original-Preproc project to diagnose divergence after extraction.
 */
class BitermParityTest {
    private static final Logger logger = LoggerFactory.getLogger(BitermParityTest.class);

    @Test
    void compareBitermsBetweenStandardAndPreprocessed() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Config config = mapper.readValue(new File("config.json"), Config.class);

        ProjectConfig dronoCfg = config.getProjects().stream()
                .filter(p -> "Dronology".equals(p.getName()))
                .findFirst()
                .orElseThrow();
        ProjectConfig preprocCfg = config.getProjects().stream()
                .filter(p -> "Dronology-Original-Preproc".equals(p.getName()))
                .findFirst()
                .orElseThrow();

        Project standard = new Project(dronoCfg);
        Project preproc = new PreprocessedProject(preprocCfg);

        // Compare per level
        compareLevel("SOURCE", standard.getSourceArtifacts(), preproc.getSourceArtifacts());
        compareLevel("INTERMEDIATE", standard.getIntermediateArtifacts(), preproc.getIntermediateArtifacts());
        compareLevel("TARGET", standard.getTargetArtifacts(), preproc.getTargetArtifacts());
    }

    private void compareLevel(String level, Set<Artifact> stdArtifacts, Set<Artifact> preprocArtifacts) {
        Map<String, Set<String>> stdMap = toBitermStringMap(stdArtifacts);
        Map<String, Set<String>> preMap = toBitermStringMap(preprocArtifacts);

        Set<String> commonIds = new HashSet<>(stdMap.keySet());
        commonIds.retainAll(preMap.keySet());

        double avgJaccard = 0.0;
        int counted = 0;
        List<Double> jaccards = new ArrayList<>();

        for (String id : commonIds) {
            Set<String> a = stdMap.getOrDefault(id, Set.of());
            Set<String> b = preMap.getOrDefault(id, Set.of());
            double j = jaccard(a, b);
            jaccards.add(j);
            avgJaccard += j;
            counted++;
        }
        avgJaccard = counted > 0 ? avgJaccard / counted : 0.0;

        // Coverage stats
        double coverageStdInPre = commonIds.size() / (double) Math.max(1, stdMap.size());
        double coveragePreInStd = commonIds.size() / (double) Math.max(1, preMap.size());

        // Size stats
        double avgStdSize =
                stdMap.values().stream().mapToInt(Set::size).average().orElse(0.0);
        double avgPreSize =
                preMap.values().stream().mapToInt(Set::size).average().orElse(0.0);

        logger.info(
                "[BITERM-COMPARE] Level={} commonIds={} coverageStdInPre={}"
                        + " coveragePreInStd={} avgStdSize={} avgPreSize={} avgJaccard={}",
                level,
                commonIds.size(),
                round4(coverageStdInPre),
                round4(coveragePreInStd),
                round4(avgStdSize),
                round4(avgPreSize),
                round4(avgJaccard));

        // Log lowest Jaccard pairs for inspection
        List<String> worst = commonIds.stream()
                .sorted(Comparator.comparingDouble(id -> jaccard(stdMap.get(id), preMap.get(id))))
                .limit(5)
                .collect(Collectors.toList());
        for (String id : worst) {
            double j = jaccard(stdMap.get(id), preMap.get(id));
            logger.info("[BITERM-COMPARE] Level={} id={} jaccard={}", level, id, round4(j));
        }

        // Ensure we have overlap to analyze
        assertFalse(
                commonIds.isEmpty(),
                "No overlapping artifact identifiers between standard and preprocessed at level " + level);
    }

    private Map<String, Set<String>> toBitermStringMap(Set<Artifact> artifacts) {
        Map<String, Set<String>> map = new HashMap<>();
        for (Artifact a : artifacts) {
            // Use canonical biterm.toString() so both sides are compared in the same representation
            Set<String> terms = a.getBiterms().stream().map(Biterm::toString).collect(Collectors.toSet());
            map.put(a.getIdentifier(), terms);
        }
        return map;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (inter.size() / (double) union.size());
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}

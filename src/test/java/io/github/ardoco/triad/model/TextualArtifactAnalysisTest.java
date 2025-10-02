/* Licensed under MIT 2025. */
package io.github.ardoco.triad.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A test class for analyzing biterm extraction from specific textual artifacts.
 */
class TextualArtifactAnalysisTest {

    private static final Logger logger = LoggerFactory.getLogger(TextualArtifactAnalysisTest.class);

    /**
     * Helper method to convert the Set of Biterm objects into a simple Map for easy comparison.
     */
    private Map<String, Integer> getBitermMap(Set<Biterm> biterms) {
        Map<String, Integer> map = new HashMap<>();
        for (Biterm biterm : biterms) {
            map.put(biterm.toString(), biterm.getWeight());
        }
        return map;
    }

    @Test
    @DisplayName("Analyze Biterm Extraction for a specific textual artifact")
    void analyzeTextualArtifact() {
        String rawText =
                "If a UAV does not have any pending flight plans  then when it reaches the final waypoint of its current flight plan  the _SingleUAVFlightPlanScheduler_ shall notify the UAV to hover in place.";

        Artifact artifact = new RequirementsDocumentArtifact("TextAnalysis", rawText);

        // Get and log the raw dependency relations
        Map<String, String> bitermRelations = artifact.getBitermRelations();
        String relationsString = bitermRelations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> String.format("%-40s (Relation: %s)", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("\n"));
        logger.info("Extracted Biterm Relations:\n{}", relationsString);

        // Get and log the final, processed biterms
        Map<String, Integer> actualBiterms = getBitermMap(artifact.getBiterms());
        String bitermsString = actualBiterms.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> String.format("%-25s = %d", "\"" + entry.getKey() + "\"", entry.getValue()))
                .collect(Collectors.joining(", "));
        logger.info("Final Extracted Biterms: {}", bitermsString);

        // This test will always pass, allowing you to inspect the output.
        assertThat(true).isTrue();
    }
}

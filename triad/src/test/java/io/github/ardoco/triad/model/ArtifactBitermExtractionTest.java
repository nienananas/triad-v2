package io.github.ardoco.triad.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the biterm extraction process from {@link Artifact} classes to ensure the
 * resulting biterms and their frequencies match the intermediate output from the original TRIAD implementation.
 */
class ArtifactBitermExtractionTest {

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
    @DisplayName("Test Biterm Extraction for RE-8.txt")
    void testBitermExtraction_RE8() {
        String rawText = "[SUMMARY]\n" +
                         "UAV State transitions\n" +
                         "[DESCRIPTION]\n" +
                         "When requested  the _VehicleCore_ shall shall transition the UAV between states according to allowed state transitions as depicted in the UAV state transition diagram";

        Map<String, Integer> expectedBiterms = Map.of(
                "stateUav", 2,
                "stateTransit", 2,
                "uavState", 2,
                "transitState", 2
        );

        Artifact artifact = new RequirementsDocumentArtifact("RE-8", rawText);
        Map<String, Integer> actualBiterms = getBitermMap(artifact.getBiterms());

        assertThat(actualBiterms).isEqualTo(expectedBiterms);
    }

    @Test
    @DisplayName("Test Biterm Extraction for RE-541.txt")
    void testBitermExtraction_RE541() {
        String rawText = "[SUMMARY]\n" +
                         "Pre-defined flight patterns\n" +
                         "[DESCRIPTION]\n" +
                         "The mission planner shall manage pre-defined flight patterns.";

        Map<String, Integer> expectedBiterms = Map.of(
                "patternFlight", 2,
                "plannerMission", 1,
                "managFlight", 1,
                "flightPattern", 2,
                "missionPlanner", 1
        );

        Artifact artifact = new RequirementsDocumentArtifact("RE-541", rawText);
        Map<String, Integer> actualBiterms = getBitermMap(artifact.getBiterms());

        assertThat(actualBiterms).isEqualTo(expectedBiterms);
    }

    @Test
    @DisplayName("Test Biterm Extraction for DD-537.txt")
    void testBitermExtraction_DD537() {
        String rawText = "[SUMMARY]\n" +
                         "Mission Plan Synchronization Points\n" +
                         "[DESCRIPTION]\n" +
                         "Flight plans for UAVs can be synchronized by inserting shared synchronization points in the order of the flight routes assigned to participating UAVs.";

        Map<String, Integer> expectedBiterms = Map.of(
                "pointSynchron", 2,
                "missionPlan", 1,
                "assignUav", 1,
                "routAssign", 1,
                "planUav", 1,
                "planFlight", 1,
                "routFlight", 1
        );

        Artifact artifact = new DesignDocumentArtifact("DD-537", rawText);
        Map<String, Integer> actualBiterms = getBitermMap(artifact.getBiterms());

        assertThat(actualBiterms).isEqualTo(expectedBiterms);
    }

    @Test
    @DisplayName("Test Biterm Extraction for DD-688.txt")
    void testBitermExtraction_DD688() {
        String rawText = "[SUMMARY]\n" +
                         "Periodically update UAV locations\n" +
                         "[DESCRIPTION]\n" +
                         "The UI shall periodically request the current location of all registered UAVs from the UI middleware and update their location on the map.";
        
        Map<String, Integer> expectedBiterms = Map.of(
            "locatUav", 1,
            "registUav", 1,
            "updatLocat", 2
        );

        Artifact artifact = new DesignDocumentArtifact("DD-688", rawText);
        Map<String, Integer> actualBiterms = getBitermMap(artifact.getBiterms());
        
        assertThat(actualBiterms).isEqualTo(expectedBiterms);
    }

    /**
     * NOTE: This test case is special. The original TRIAD implementation processed source code differently than the
     * new tree-sitter based approach. It likely concatenated extracted elements (class names, method names, comments)
     * into a single text block for analysis.
     * <p>
     * This test verifies that our dependency-parsing biterm extraction (`getBitermsFromText`) produces the correct
     * output when given the *processed text* that the original implementation would have generated. This allows us to
     * validate the core biterm extraction logic in isolation.
     */
    @Test
    @DisplayName("Test Biterm Extraction from Processed Code Text (FlightManagerService)")
    void testBitermExtraction_FromProcessedCodeText() {
        String processedCodeText = "flight manag servic allow servic handl flight uav allow assign flight plan uav allow send flight relat command uav take return home singleton configur servic instanc";

        Map<String, Integer> expectedBiterms = Map.of(
            "sendUav", 1,
            "returnHome", 4,
            "sendCommand", 1,
            "assignPlan", 1,
            "assignUav", 1,
            "flightUav", 1,
            "planFlight", 6
        );

        // We use a textual artifact here to directly test the `getBitermsFromText` method,
        // which is the core logic shared across artifact types for dependency-based biterm extraction.
        Artifact artifact = new RequirementsDocumentArtifact("FlightManagerService", processedCodeText);
        // Manually set the text body to the already processed text
        artifact.textBody = processedCodeText;
        Map<String, Integer> actualBiterms = getBitermMap(artifact.getBiterms());

        assertThat(actualBiterms).isEqualTo(expectedBiterms);
    }
}
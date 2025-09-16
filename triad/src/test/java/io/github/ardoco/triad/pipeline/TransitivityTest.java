package io.github.ardoco.triad.pipeline;

import io.github.ardoco.triad.ir.SimilarityMatrix;
import io.github.ardoco.triad.ir.SingleLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TransitivityTest {
    
    private SimilarityMatrix sourceIntermediateMatrix;
    private SimilarityMatrix intermediateTargetMatrix;
    private SimilarityMatrix sourceSourceMatrix;
    private SimilarityMatrix intermediateIntermediateMatrix;
    
    @BeforeEach
    void setUp() {
        // Create test similarity matrices
        sourceIntermediateMatrix = new SimilarityMatrix();
        intermediateTargetMatrix = new SimilarityMatrix();
        sourceSourceMatrix = new SimilarityMatrix();
        intermediateIntermediateMatrix = new SimilarityMatrix();
        
        // Add some test links
        // Source S1 -> Intermediate I1 (0.8)
        sourceIntermediateMatrix.addLink("S1", "I1", 0.8);
        sourceIntermediateMatrix.addLink("S1", "I2", 0.6);
        sourceIntermediateMatrix.addLink("S2", "I1", 0.7);
        sourceIntermediateMatrix.addLink("S2", "I2", 0.5);
        
        // Intermediate I1 -> Target T1 (0.9)
        intermediateTargetMatrix.addLink("I1", "T1", 0.9);
        intermediateTargetMatrix.addLink("I1", "T2", 0.4);
        intermediateTargetMatrix.addLink("I2", "T1", 0.3);
        intermediateTargetMatrix.addLink("I2", "T2", 0.8);
        
        // Source-Source similarities
        sourceSourceMatrix.addLink("S1", "S2", 0.6);
        sourceSourceMatrix.addLink("S2", "S1", 0.6);
        
        // Intermediate-Intermediate similarities
        intermediateIntermediateMatrix.addLink("I1", "I2", 0.5);
        intermediateIntermediateMatrix.addLink("I2", "I1", 0.5);
    }
    
    @Test
    void testTransitivityDoesNotDegradePerformance() {
        // Create a base matrix with known good performance
        SimilarityMatrix baseMatrix = new SimilarityMatrix();
        baseMatrix.addLink("S1", "T1", 0.7); // High precision link
        baseMatrix.addLink("S1", "T2", 0.2); // Low precision link
        baseMatrix.addLink("S2", "T1", 0.3);
        baseMatrix.addLink("S2", "T2", 0.6);
        
        // Apply transitivity
        Transitivity transitivity = new Transitivity(
            sourceIntermediateMatrix, 
            intermediateTargetMatrix,
            sourceSourceMatrix, 
            intermediateIntermediateMatrix
        );
        
        SimilarityMatrix result = transitivity.applyTransitivity(baseMatrix);
        
        // Transitivity should not significantly degrade high-confidence links
        double originalS1T1 = baseMatrix.getScore("S1", "T1");
        double transitiveS1T1 = result.getScore("S1", "T1");
        
        System.out.println("Original S1->T1: " + originalS1T1);
        System.out.println("Transitive S1->T1: " + transitiveS1T1);
        
        // The transitive score should not be dramatically lower than the original
        assertTrue(transitiveS1T1 >= originalS1T1 * 0.8, 
            "Transitivity should not degrade high-confidence links by more than 20%");
    }
    
    @Test
    void testTransitivityAddsNewLinks() {
        // Start with an empty base matrix
        SimilarityMatrix baseMatrix = new SimilarityMatrix();
        
        Transitivity transitivity = new Transitivity(
            sourceIntermediateMatrix, 
            intermediateTargetMatrix,
            sourceSourceMatrix, 
            intermediateIntermediateMatrix
        );
        
        SimilarityMatrix result = transitivity.applyTransitivity(baseMatrix);
        
        // Should discover transitive links
        // S1 -> I1 (0.8) -> T1 (0.9) should create S1 -> T1 link
        double s1t1Score = result.getScore("S1", "T1");
        System.out.println("Discovered S1->T1 transitive score: " + s1t1Score);
        
        assertTrue(s1t1Score > 0, "Transitivity should discover new links");
    }
    
    @Test
    void testTransitivityParameters() {
        SimilarityMatrix baseMatrix = new SimilarityMatrix();
        baseMatrix.addLink("S1", "T1", 0.5);
        
        Transitivity transitivity = new Transitivity(
            sourceIntermediateMatrix, 
            intermediateTargetMatrix,
            sourceSourceMatrix, 
            intermediateIntermediateMatrix
        );
        
        // Test that transitivity doesn't create unreasonably many links
        SimilarityMatrix result = transitivity.applyTransitivity(baseMatrix);
        
        int linkCount = 0;
        for (String source : result.getSourceArtifacts()) {
            if (result.getLinks(source) != null) {
                linkCount += result.getLinks(source).size();
            }
        }
        
        System.out.println("Total links created: " + linkCount);
        
        // Should not create an excessive number of links (this might be the issue)
        assertTrue(linkCount < 20, "Transitivity should not create excessive links");
    }
}

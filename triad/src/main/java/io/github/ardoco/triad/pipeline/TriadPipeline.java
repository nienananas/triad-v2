package io.github.ardoco.triad.pipeline;

import io.github.ardoco.triad.ir.ArtifactsCollection;
import io.github.ardoco.triad.ir.IRModel;
import io.github.ardoco.triad.ir.IRUnion;
import io.github.ardoco.triad.ir.SimilarityMatrix;
import io.github.ardoco.triad.model.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TriadPipeline {
    private static final Logger logger = LoggerFactory.getLogger(TriadPipeline.class);

    private final Project project;
    private final IRModel irModel;

    public TriadPipeline(Project project, IRModel irModel) {
        this.project = project;
        this.irModel = irModel;
    }

    public SimilarityMatrix run() throws IOException {
        logger.info("Starting TRIAD pipeline for project '{}' with IR model '{}'", project.getName(), irModel.getModelName());

        ArtifactsCollection sourceCollection = new ArtifactsCollection(project.getSourceArtifacts());
        ArtifactsCollection targetCollection = new ArtifactsCollection(project.getTargetArtifacts());
        ArtifactsCollection intermediateCollection = new ArtifactsCollection(project.getIntermediateArtifacts());

        SimilarityMatrix irOnlyBaseMatrix = irModel.Compute(sourceCollection, targetCollection);
        logger.info("Computed IR-ONLY baseline matrix.");

        // If there is no intermediate artifact set, run in TAROT-ONLY mode.
        if (intermediateCollection.size() == 0) {
            logger.info("No intermediate artifacts for project '{}': running TAROT-ONLY mode.", project.getName());

            SimilarityMatrix unionSourceTargetSim = IRUnion.computeUnion(sourceCollection, targetCollection);
            SimilarityMatrix unionTargetSourceSim = IRUnion.computeUnion(targetCollection, sourceCollection);

            TarotOnlyEnrichment tarot = new TarotOnlyEnrichment(project, irModel,
                    unionSourceTargetSim, unionTargetSourceSim);
            SimilarityMatrix tarotSTMatrix = tarot.enrichAndFuse();

            SimilarityMatrix fusedMatrix = fuseAverage(irOnlyBaseMatrix, tarotSTMatrix);
            logger.info("TAROT-ONLY pipeline finished.");
            return fusedMatrix;
        }

        // Otherwise, run the full TRIAD pipeline.
        logger.info("Computing union similarity matrices on original artifacts...");
        SimilarityMatrix unionSourceIntermediateSim = IRUnion.computeUnion(sourceCollection, intermediateCollection);
        SimilarityMatrix unionIntermediateTargetSim = IRUnion.computeUnion(intermediateCollection, targetCollection);
        SimilarityMatrix unionTargetIntermediateSim = IRUnion.computeUnion(targetCollection, intermediateCollection);
        SimilarityMatrix unionSourceSourceSim = IRUnion.computeUnion(sourceCollection, sourceCollection);
        SimilarityMatrix unionIntermediateIntermediateSim = IRUnion.computeUnion(intermediateCollection, intermediateCollection);
        logger.info("Union matrices computed.");

        logger.info("Starting enrichment phase...");
        var enrichment = new Enrichment(project, irModel,
                unionSourceIntermediateSim,
                unionIntermediateTargetSim,
                unionTargetIntermediateSim);
        SimilarityMatrix tarotSTMatrix = enrichment.enrichAndFuse();
        logger.info("Enrichment phase complete.");

        SimilarityMatrix fusedMatrix = fuseAverage(irOnlyBaseMatrix, tarotSTMatrix);
        logger.info("Fused IR-ONLY baseline and enriched matrix.");

        // Check if transitivity should be applied (can be disabled for testing)
        boolean applyTransitivity = Boolean.parseBoolean(System.getProperty("triad.transitivity.enabled", "true"));
        
        if (applyTransitivity) {
            var transitivity = new Transitivity(unionSourceIntermediateSim, unionIntermediateTargetSim,
                    unionSourceSourceSim, unionIntermediateIntermediateSim);
            SimilarityMatrix finalMatrix = transitivity.applyTransitivity(fusedMatrix);
            logger.info("TRIAD pipeline finished with transitivity.");
            return finalMatrix;
        } else {
            logger.info("TRIAD pipeline finished without transitivity (disabled for testing).");
            return fusedMatrix;
        }
    }

    public SimilarityMatrix runIrOnly() throws IOException {
        logger.info("Starting IR-ONLY pipeline for project '{}' with IR model '{}'", project.getName(), irModel.getModelName());
        return irModel.Compute(new ArtifactsCollection(project.getSourceArtifacts()),
                new ArtifactsCollection(project.getTargetArtifacts()));
    }

    private static SimilarityMatrix fuseConservativeMax(SimilarityMatrix base, SimilarityMatrix enriched) {
        SimilarityMatrix fused = base.deepCopy();
        for (String s : base.getSourceArtifacts()) {
            for (String t : base.getTargetArtifacts()) {
                double baseScore = base.getScore(s, t);
                double enrichedScore = enriched.getScore(s, t);
                fused.setScore(s, t, Math.max(baseScore, enrichedScore));
            }
        }
        return fused;
    }

    /**
     * Fuses two similarity matrices by averaging their scores, matching the original TRIAD implementation.
     * This is the fusion strategy used in the original TRIAD paper: 0.5 * (score1 + score2).
     */
    private static SimilarityMatrix fuseAverage(SimilarityMatrix base, SimilarityMatrix enriched) {
        SimilarityMatrix fused = base.deepCopy();
        for (String s : base.getSourceArtifacts()) {
            for (String t : base.getTargetArtifacts()) {
                double baseScore = base.getScore(s, t);
                double enrichedScore = enriched.getScore(s, t);
                fused.setScore(s, t, 0.5 * (baseScore + enrichedScore));
            }
        }
        return fused;
    }
}
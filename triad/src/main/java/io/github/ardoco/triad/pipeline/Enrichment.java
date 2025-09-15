package io.github.ardoco.triad.pipeline;

import io.github.ardoco.triad.ir.ArtifactsCollection;
import io.github.ardoco.triad.ir.IRModel;
import io.github.ardoco.triad.ir.SimilarityMatrix;
import io.github.ardoco.triad.model.Project;
import io.github.ardoco.triad.util.EnrichmentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class Enrichment {
    private static final Logger logger = LoggerFactory.getLogger(Enrichment.class);

    private final Project project;
    private final IRModel irModel;
    private final SimilarityMatrix sourceToIntermediateSim;   // rows = sources, cols = intermediates
    private final SimilarityMatrix targetToIntermediateSim;   // rows = targets, cols = intermediates

    public Enrichment(Project project,
                      IRModel irModel,
                      SimilarityMatrix sourceToIntermediateSim,
                      SimilarityMatrix intermediateToTargetSim, // Not used but retained for signature parity
                      SimilarityMatrix targetToIntermediateSim) {
        this.project = project;
        this.irModel = irModel;
        this.sourceToIntermediateSim = sourceToIntermediateSim;
        this.targetToIntermediateSim = targetToIntermediateSim;
    }

    public SimilarityMatrix enrichAndFuse() throws IOException {
        Map<String, Map<String, Integer>> intermediateBitermMap =
                EnrichmentUtils.getBitermFrequencyMap(project.getIntermediateArtifacts());

        Map<String, Map<String, Integer>> srcEnrichBiterms =
                EnrichmentUtils.selectNeighborConsensualBiterms(project.getSourceArtifacts(), intermediateBitermMap, sourceToIntermediateSim);
        EnrichmentUtils.debugEnrichmentStats("SRC+", project.getSourceArtifacts(), srcEnrichBiterms);

        Map<String, Map<String, Integer>> tgtEnrichBiterms =
                EnrichmentUtils.selectNeighborConsensualBiterms(project.getTargetArtifacts(), intermediateBitermMap, targetToIntermediateSim);
        EnrichmentUtils.debugEnrichmentStats("TGT+", project.getTargetArtifacts(), tgtEnrichBiterms);

        ArtifactsCollection extendedSources = EnrichmentUtils.createExtendedCollection(project.getSourceArtifacts(), srcEnrichBiterms, "SRC+");
        ArtifactsCollection extendedTargets = EnrichmentUtils.createExtendedCollection(project.getTargetArtifacts(), tgtEnrichBiterms, "TGT+");

        SimilarityMatrix s1 = irModel.Compute(extendedSources, new ArtifactsCollection(project.getTargetArtifacts()));
        SimilarityMatrix s2 = irModel.Compute(new ArtifactsCollection(project.getSourceArtifacts()), extendedTargets);

        return EnrichmentUtils.elementwiseAverage(s1, s2);
    }
}
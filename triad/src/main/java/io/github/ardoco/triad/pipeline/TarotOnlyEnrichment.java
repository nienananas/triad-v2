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

public class TarotOnlyEnrichment {
    private static final Logger logger = LoggerFactory.getLogger(TarotOnlyEnrichment.class);

    private final Project project;
    private final IRModel irModel;
    private final SimilarityMatrix sourceToTargetSim; // rows = sources, cols = targets
    private final SimilarityMatrix targetToSourceSim; // rows = targets, cols = sources

    public TarotOnlyEnrichment(Project project, IRModel irModel,
                               SimilarityMatrix sourceToTargetSim,
                               SimilarityMatrix targetToSourceSim) {
        this.project = project;
        this.irModel = irModel;
        this.sourceToTargetSim = sourceToTargetSim;
        this.targetToSourceSim = targetToSourceSim;
    }

    public SimilarityMatrix enrichAndFuse() throws IOException {
        Map<String, Map<String, Integer>> sourceBiterms = EnrichmentUtils.getBitermFrequencyMap(project.getSourceArtifacts());
        Map<String, Map<String, Integer>> targetBiterms = EnrichmentUtils.getBitermFrequencyMap(project.getTargetArtifacts());

        Map<String, Map<String, Double>> srcEnrichBiterms =
                EnrichmentUtils.selectNeighborConsensualBiterms(project.getSourceArtifacts(), targetBiterms, sourceToTargetSim);
        EnrichmentUtils.debugEnrichmentStats("SRC+(TAROT)", project.getSourceArtifacts(), srcEnrichBiterms);

        Map<String, Map<String, Double>> tgtEnrichBiterms =
                EnrichmentUtils.selectNeighborConsensualBiterms(project.getTargetArtifacts(), sourceBiterms, targetToSourceSim);
        EnrichmentUtils.debugEnrichmentStats("TGT+(TAROT)", project.getTargetArtifacts(), tgtEnrichBiterms);

        ArtifactsCollection extendedSources = EnrichmentUtils.createExtendedCollection(project.getSourceArtifacts(), srcEnrichBiterms, "SRC+");
        ArtifactsCollection extendedTargets = EnrichmentUtils.createExtendedCollection(project.getTargetArtifacts(), tgtEnrichBiterms, "TGT+");

        SimilarityMatrix s1 = irModel.Compute(extendedSources, new ArtifactsCollection(project.getTargetArtifacts()));
        SimilarityMatrix s2 = irModel.Compute(new ArtifactsCollection(project.getSourceArtifacts()), extendedTargets);
        return EnrichmentUtils.elementwiseMax(s1, s2);
    }
}
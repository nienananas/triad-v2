/* Licensed under MIT 2025. */
package io.github.ardoco;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ardoco.Biterm.Biterm;
import io.github.ardoco.Biterm.ConsensualBiterm;
import io.github.ardoco.artifact.RequirementsDocumentArtifact;
import io.github.ardoco.artifact.SourceCodeArtifact;
import io.github.ardoco.document.ArtifactsCollection;
import io.github.ardoco.model.IRModel;
import io.github.ardoco.model.VSM;
import io.github.ardoco.model.SingleLink;
import io.github.ardoco.util.OutputLog;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws IOException {
        Project project = new Project("Dronology_small");

        Set<RequirementsDocumentArtifact> requirementsArtifacts = project.getRequirementsArtifacts();
        Set<DesignDocumentArtifact> designArtifacts = project.getDesignArtifacts();
        Set<SourceCodeArtifact> codeArtifacts = project.getCodeArtifacts();

        logger.info("Design artifacts: {}", project.getDesignArtifacts().size());
        logger.info("Code artifacts: {}", codeArtifacts.size());
        logger.info("Requirements artifacts: {}", requirementsArtifacts.size());

        logger.info("Finding consensual biterms...");

        Set<ConsensualBiterm> designCodeConsensualBiterms = new HashSet<>();
        Set<ConsensualBiterm> designReqConsensualBiterms = new HashSet<>();


        // design as intermediate
        int i = 0;
        for (DesignDocumentArtifact designDocumentArtifact : designArtifacts) {
            logger.info("Processing design artifact: {}", ++i);
            Set<Biterm> designBiterms = designDocumentArtifact.getBiterms();
            for (Biterm designBiterm : designBiterms) {
                // code artifacts
                for (SourceCodeArtifact codeArtifact : codeArtifacts) {
                    Set<Biterm> codeBiterms = codeArtifact.getBiterms();
                    for (Biterm codeBiterm : codeBiterms) {
                        if (requirementBiterm.isConsensual(codeBiterm)) {
                            ConsensualBiterm consensualBiterm = new ConsensualBiterm(
                                    requirementBiterm,
                                    designDocumentArtifact.getIdentifier(),
                                    codeArtifact.getIdentifier());
                            designCodeConsensualBiterms.add(consensualBiterm);
                            designDocumentArtifact.addConsensualBiterm(consensualBiterm);
                            codeArtifact.addConsensualBiterm(consensualBiterm);
                        }
                    }
                }
                // requirements artifacts
                for (RequirementsDocumentArtifact requirementsArtifact : requirementsArtifacts) {
                    Set<Biterm> requirementBiterms = requirementsArtifact.getBiterms();
                    for (Biterm requirementBiterm : requirementBiterms) {
                        if (designBiterm.isConsensual(requirementBiterm)) {
                            ConsensualBiterm consensualBiterm = new ConsensualBiterm(
                                    designBiterm,
                                    designDocumentArtifact.getIdentifier(),
                                    requirementsArtifact.getIdentifier());
                            designReqConsensualBiterms.add(consensualBiterm);
                            designDocumentArtifact.addConsensualBiterm(consensualBiterm);
                            requirementsArtifact.addConsensualBiterm(consensualBiterm);
                        }
                    }
                }
            }
        }

        OutputLog.writeBitermsToFile(
                "output/" + project.getName() + "/Design-Code-ConsensualBiterms.txt", designCodeConsensualBiterms);
        OutputLog.writeBitermsToFile(
                "output/" + project.getName() + "/Design-Requirements-ConsensualBiterms.txt", designReqConsensualBiterms);

        // extend artifacts with consensual biterms
        IRModel vsm = new VSM();
        ArtifactsCollection reqCollection = new ArtifactsCollection(requirementsArtifacts);
        ArtifactsCollection designCollection = new ArtifactsCollection(designArtifacts);
        ArtifactsCollection codeCollection = new ArtifactsCollection(codeArtifacts);

        // calculate similarity pair wise
        var reqDesignSimilarity = vsm.Compute(reqCollection, designCollection);
        var designCodeSimilarity = vsm.Compute(designCollection, codeCollection);
        var reqCodeSimilarity = vsm.Compute(reqCollection, codeCollection);

        // write each similarity matrix to file
        OutputLog.writeSimilarityMatrixToFile(
                "output/" + project.getName() + "/similarity_req_design.csv", similarityMatrix);
        OutputLog.writeSimilarityMatrixToFile(
                "output/" + project.getName() + "/similarity_design_code.csv", designCodeSimilarity);
        OutputLog.writeSimilarityMatrixToFile(
                "output/" + project.getName() + "/similarity_req_code.csv", reqCodeSimilarity);

        // TODO: Re-rank based on transitivity
        

        // TODO: adjust threshold for re-ordered links after applying transitivity
        List<SingleLink> allLinks = similarityMatrix.getAllLinks();
        List<SingleLink> nonZeroLinks = allLinks.stream()
                .filter(link -> link.getScore() > 0.0)
                .collect(Collectors.toList());

        if (!nonZeroLinks.isEmpty()) {
            nonZeroLinks.sort(Comparator.comparingDouble(SingleLink::getScore));
            int cutoffIndex = (int) Math.floor(nonZeroLinks.size() * 0.25);
            double threshold = nonZeroLinks.get(cutoffIndex).getScore();

            List<SingleLink> topLinks = allLinks.stream()
                    .filter(link -> link.getScore() >= threshold)
                    .sorted(Comparator.comparingDouble(SingleLink::getScore).reversed())
                    .collect(Collectors.toList());

            logger.info("Top Traceability Links (Threshold: {}):", String.format("%.4f", threshold));
            for (SingleLink link : topLinks) {
                logger.info("  {} -> {} (Score: {})", link.getSourceArtifactId(), link.getTargetArtifactId(), String.format("%.4f", link.getScore()));
            }
            try {
                OutputLog.writeTraceabilityLinksToFile(
                        "output/" + project.getName() + "/RE-Code-TraceabilityLinks.csv", topLinks);
            } catch (IOException e) {
                logger.error("Failed to write traceability links to file", e);
            }
        } else {
            logger.info("No non-zero traceability links found.");
        }
    }

    
}

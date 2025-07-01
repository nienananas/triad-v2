package io.github.ardoco;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ardoco.Biterm.Biterm;
import io.github.ardoco.Biterm.ConsensualBiterm;
import io.github.ardoco.artifact.RequirementsDocumentArtifact;
import io.github.ardoco.artifact.SourceCodeArtifact;
import io.github.ardoco.document.ArtifactsCollection;
import io.github.ardoco.model.IRModel;
import io.github.ardoco.model.LinksList;
import io.github.ardoco.model.VSM;
import io.github.ardoco.util.OutputLog;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    public static void main(String[] args) throws IOException {
        Project project = new Project("Dronology");

        Set<SourceCodeArtifact> codeArtifacts = project.getCodeArtifacts();
        Set<RequirementsDocumentArtifact> requirementsArtifacts = project.getRequirementsArtifacts();

        logger.info("Code artifacts: {}", codeArtifacts.size());
        logger.info("Requirements artifacts: {}", requirementsArtifacts.size());

        logger.info("Finding consensual biterms...");

        Set<ConsensualBiterm> reCodeConsensualBiterms = new HashSet<>();

        int i = 0;
        for (RequirementsDocumentArtifact requirementsArtifact : requirementsArtifacts) {
            logger.info("Processing requirements artifact: {}", ++i);

            Set<Biterm> requirementBiterms = requirementsArtifact.getBiterms();
            for (Biterm requirementBiterm : requirementBiterms) {
                for (SourceCodeArtifact codeArtifact : codeArtifacts) {
                    Set<Biterm> codeBiterms = codeArtifact.getBiterms();
                    for (Biterm codeBiterm : codeBiterms) {
                        if (requirementBiterm.isConsensual(codeBiterm)) {
                            //logger.info("Found consensual biterm: {} in {} and {}", requirementBiterm, requirementsArtifact.getIdentifier(), codeArtifact.getIdentifier());
                            ConsensualBiterm consensualBiterm = new ConsensualBiterm(requirementBiterm, requirementsArtifact.getIdentifier(), codeArtifact.getIdentifier());
                            reCodeConsensualBiterms.add(consensualBiterm);
                            requirementsArtifact.addConsensualBiterm(consensualBiterm);
                            codeArtifact.addConsensualBiterm(consensualBiterm);
                        }
                    }
                }
            }
        }
        OutputLog.writeBitermsToFile("output/" + project.getName() + "/RE-Code-ConsensualBiterms.txt", reCodeConsensualBiterms);

        // extend artifacts with consensual biterms
        IRModel vsm = new VSM();
        ArtifactsCollection reqCollection = new ArtifactsCollection(requirementsArtifacts);
        ArtifactsCollection codeCollection = new ArtifactsCollection(codeArtifacts);

        var similarityMatrix = vsm.Compute(reqCollection, codeCollection);

        // write similarity matrix to file
        OutputLog.writeSimilarityMatrixToFile("output/" + project.getName() + "/similarityMatrix.txt", similarityMatrix);
    }
}

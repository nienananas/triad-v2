package io.github.ardoco;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import io.github.ardoco.Biterm.Biterm;
import io.github.ardoco.Biterm.ConsensualBiterm;
import io.github.ardoco.artifact.RequirementsDocumentArtifact;
import io.github.ardoco.artifact.SourceCodeArtifact;
import io.github.ardoco.util.OutputLog;

/**
 * Hello world!
 *
 */
public class App {
    public static void main(String[] args) throws IOException {
        Project project = new Project("Dronology");

        Set<SourceCodeArtifact> codeArtifacts = project.getCodeArtifacts();
        Set<RequirementsDocumentArtifact> requirementsArtifacts = project.getRequirementsArtifacts();

        System.out.println("Code artifacts: " + codeArtifacts.size());
        System.out.println("Requirements artifacts: " + requirementsArtifacts.size());

        System.out.println("Finding consensual biterms...");

        Set<ConsensualBiterm> consensualBiterms = new HashSet<>();

        int i = 0;
        for (RequirementsDocumentArtifact requirementsArtifact : requirementsArtifacts) {
            System.out.println("Processing requirements artifact: " + ++i);

            Set<Biterm> requirementBiterms = requirementsArtifact.getBiterms();
            for (Biterm requirementBiterm : requirementBiterms) {
                for (SourceCodeArtifact codeArtifact : codeArtifacts) {
                    Set<Biterm> codeBiterms = codeArtifact.getBiterms();
                    for (Biterm codeBiterm : codeBiterms) {
                        if (requirementBiterm.isConsensual(codeBiterm)) {
                            //System.out.println("Found consensual biterm: " + requirementBiterm + " in " + requirementsArtifact.getIdentifier() + " and " + codeArtifact.getIdentifier());
                            consensualBiterms.add(new ConsensualBiterm(requirementBiterm, requirementsArtifact.getIdentifier(), codeArtifact.getIdentifier()));
                        }
                    }
                }
            }
            OutputLog.writeBitermsToFile("output/" + project.getName() + "/consensualBiterms/"  + requirementsArtifact.getIdentifier() + ".txt", consensualBiterms);
        }
    }
}

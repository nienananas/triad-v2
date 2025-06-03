package io.github.ardoco;

import java.lang.module.ModuleDescriptor.Requires;
import java.util.List;
import java.util.Set;

import io.github.ardoco.Biterm.Biterm;
import io.github.ardoco.artifact.Artifact;
import io.github.ardoco.artifact.RequirementsDocumentArtifact;
import io.github.ardoco.artifact.SourceCodeArtifact;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );
        // example RequirementsDocumentArtifact
        Artifact artifact_code = new SourceCodeArtifact("public abstract class AbstractDrone extends Observable implements IDrone {\n");
        Artifact artifact_requirements = new RequirementsDocumentArtifact("A fake email is sent to the LHCP.");

        Set<Biterm> biterms = artifact_code.getBiterms();
        biterms.forEach(biterm -> {
            System.out.println("Biterm: " + biterm);
        });
    }
}

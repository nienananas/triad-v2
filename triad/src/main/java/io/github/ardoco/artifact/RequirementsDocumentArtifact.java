package io.github.ardoco.artifact;

public class RequirementsDocumentArtifact extends Artifact {

    public RequirementsDocumentArtifact(String identifier, String textBody) {
        super(identifier, textBody);
    }

    @Override
    protected void preProcessing() {
        System.out.println("Preprocessing Requirements Document Artifact");
    }
} 
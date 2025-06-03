package io.github.ardoco.artifact;

public class RequirementsDocumentArtifact extends Artifact {

    public RequirementsDocumentArtifact(String textBody) {
        super(textBody);
    }

    @Override
    protected void preProcessing() {
        System.out.println("Preprocessing Requirements Document Artifact");
    }
} 
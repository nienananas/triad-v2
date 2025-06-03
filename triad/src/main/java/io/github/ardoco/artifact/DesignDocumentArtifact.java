package io.github.ardoco.artifact;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DesignDocumentArtifact extends Artifact {

    public DesignDocumentArtifact(String textBody) {
        super(textBody);
    }

    @Override
    protected void preProcessing() {
        System.out.println("Preprocessing Design Document Artifact");
        // nothing to do for design documents
    }
} 
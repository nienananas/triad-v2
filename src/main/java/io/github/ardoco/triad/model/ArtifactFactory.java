package io.github.ardoco.triad.model;

public class ArtifactFactory {
    public static Artifact create(String identifier, String textBody, ArtifactType type) {
        return switch (type) {
            case TEXTUAL -> new RequirementsDocumentArtifact(identifier, textBody);
            case JAVA_CODE -> new JavaCodeArtifact(identifier, textBody);
            case C_CODE -> new CCodeArtifact(identifier, textBody);
            default -> throw new IllegalArgumentException("Unknown artifact type: " + type);
        };
    }
}
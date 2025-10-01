package io.github.ardoco.triad.config;

import io.github.ardoco.triad.model.ArtifactType;

public class ArtifactConfig {
    private String path;
    private ArtifactType type;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public ArtifactType getType() { return type; }
    public void setType(ArtifactType type) { this.type = type; }
}
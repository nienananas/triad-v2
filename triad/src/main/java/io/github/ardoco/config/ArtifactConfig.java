package io.github.ardoco.config;

import io.github.ardoco.model.ArtifactType;

public class ArtifactConfig {
    private String path;
    private ArtifactType type;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public ArtifactType getType() { return type; }
    public void setType(ArtifactType type) { this.type = type; }
}
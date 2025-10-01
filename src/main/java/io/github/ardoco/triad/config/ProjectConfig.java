/* Licensed under MIT 2025. */
package io.github.ardoco.triad.config;

public class ProjectConfig {
    private String name;
    private ArtifactConfig source;
    private ArtifactConfig intermediate;
    private ArtifactConfig target;
    private String goldStandardPath;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArtifactConfig getSource() {
        return source;
    }

    public void setSource(ArtifactConfig source) {
        this.source = source;
    }

    public ArtifactConfig getIntermediate() {
        return intermediate;
    }

    public void setIntermediate(ArtifactConfig intermediate) {
        this.intermediate = intermediate;
    }

    public ArtifactConfig getTarget() {
        return target;
    }

    public void setTarget(ArtifactConfig target) {
        this.target = target;
    }

    public String getGoldStandardPath() {
        return goldStandardPath;
    }

    public void setGoldStandardPath(String goldStandardPath) {
        this.goldStandardPath = goldStandardPath;
    }
}

/* Licensed under MIT 2025. */
package io.github.ardoco.triad.config;

import java.util.List;

public class Config {
    private List<ProjectConfig> projects;

    public List<ProjectConfig> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectConfig> projects) {
        this.projects = projects;
    }
}

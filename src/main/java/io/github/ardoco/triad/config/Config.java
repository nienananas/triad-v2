/* Licensed under MIT 2025. */
package io.github.ardoco.triad.config;

import java.util.List;

public class Config {

    /**
     * Boolean indicating if an evaluation should be carried out.
     */
    private boolean doEvaluate;
    /**
     * Data specifying the projects on which the approach(es) should be executed.
     */
    private List<ProjectConfig> projects;

    public boolean getDoEvaluate() {
        return doEvaluate;
    }

    public void setDoEvaluate(boolean doEvaluate) {
        this.doEvaluate = doEvaluate;
    }

    public List<ProjectConfig> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectConfig> projects) {
        this.projects = projects;
    }
}

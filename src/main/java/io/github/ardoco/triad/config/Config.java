/* Licensed under MIT 2025. */
package io.github.ardoco.triad.config;

import java.util.List;

public class Config {

    /**
     * Boolean indicating if an evaluation should be carried out.
     */
    private boolean doEvaluate;
    /**
     * The name of the IR method that should be used.
     */
    private String irMethod;
    /**
     * Boolean indicating if the triad method with the given IR method should be executed or only the IR method.
     */
    private boolean runTriad;
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

    public String getIrMethod() {
        return irMethod;
    }

    public void setIrMethod(String irMethod) {
        this.irMethod = irMethod;
    }

    public boolean getRunTriad() {
        return runTriad;
    }

    public void getRunTriad(boolean runTriad) {
        this.runTriad = runTriad;
    }


    public List<ProjectConfig> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectConfig> projects) {
        this.projects = projects;
    }
}

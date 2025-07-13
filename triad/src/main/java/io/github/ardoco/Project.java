/* Licensed under MIT 2025. */
package io.github.ardoco;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ardoco.artifact.DesignDocumentArtifact;
import io.github.ardoco.artifact.RequirementsDocumentArtifact;
import io.github.ardoco.artifact.SourceCodeArtifact;

public class Project {
    private static final Logger logger = LoggerFactory.getLogger(Project.class);
    private final String projectName;
    private final Path codePath;
    private final Path reqsPath;
    private final Path designPath;

    public Project(String projectName) {
        this.projectName = projectName;
        this.codePath = Paths.get("dataset/" + this.projectName + "/Code/");
        this.designPath = Paths.get("dataset/" + this.projectName + "/Design/");
        this.reqsPath = Paths.get("dataset/" + this.projectName + "/Requirements/");
    }

    public Set<SourceCodeArtifact> getCodeArtifacts() throws IOException {
        try (Stream<Path> files = Files.walk(this.codePath)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            String content = Files.readString(path);
                            String name = path.getFileName().toString().replace(".java", "");
                            return new SourceCodeArtifact(name, content);
                        } catch (IOException e) {
                            logger.error("Error reading file: " + path, e);
                            return null;
                        }
                    })
                    .filter(a -> a != null)
                    .collect(Collectors.toSet());
        }
    }

    public Set<RequirementsDocumentArtifact> getRequirementsArtifacts() throws IOException {
        try (Stream<Path> files = Files.walk(this.reqsPath)) {
            return files.filter(p -> p.toString().endsWith(".txt"))
                    .map(path -> {
                        try {
                            String content = Files.readString(path);
                            String identifier = path.getFileName().toString().replace(".txt", "");
                            return new RequirementsDocumentArtifact(identifier, content);
                        } catch (IOException e) {
                            logger.error("Error reading file: " + path, e);
                            return null;
                        }
                    })
                    .filter(a -> a != null)
                    .collect(Collectors.toSet());
        }
    }

    public Set<DesignDocumentArtifact> getDesignArtifacts() throws IOException {
        try (Stream<Path> files = Files.walk(this.designPath)) {
            return files.filter(p -> p.toString().endsWith(".txt"))
                    .map(path -> {
                        try {
                            String content = Files.readString(path);
                            String identifier = path.getFileName().toString().replace(".txt", "");
                            return new DesignDocumentArtifact(identifier, content);
                        } catch (IOException e) {
                            logger.error("Error reading file: " + path, e);
                            return null;
                        }
                    })
                    .filter(a -> a != null)
                    .collect(Collectors.toSet());
        }
    }

    public String getName() {
        return projectName;
    }
}

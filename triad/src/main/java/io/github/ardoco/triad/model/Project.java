package io.github.ardoco.triad.model;

import io.github.ardoco.triad.config.ArtifactConfig;
import io.github.ardoco.triad.config.ProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Project {
    private static final Logger logger = LoggerFactory.getLogger(Project.class);
    private final ProjectConfig config;

    public Project(ProjectConfig config) {
        this.config = config;
    }
    
    /**
     * Provides subclasses with access to the project configuration.
     */
    protected ProjectConfig getConfig() {
        return this.config;
    }

    public Set<Artifact> getSourceArtifacts() throws IOException {
        return loadArtifacts(config.getSource());
    }

    public Set<Artifact> getIntermediateArtifacts() throws IOException {
        ArtifactConfig intermediateConfig = config.getIntermediate();
        if (intermediateConfig == null) {
            return java.util.Collections.emptySet();
        }
        return loadArtifacts(intermediateConfig);
    }

    public Set<Artifact> getTargetArtifacts() throws IOException {
        return loadArtifacts(config.getTarget());
    }

    protected Set<Artifact> loadArtifacts(ArtifactConfig artifactConfig) throws IOException {
        if (artifactConfig == null) {
            return java.util.Collections.emptySet();
        }
        Path root = Paths.get("dataset").resolve(artifactConfig.getPath());

        try (Stream<Path> files = Files.walk(root)) {
            return files
                .filter(Files::isRegularFile)
                .map(filePath -> {
                    try {
                        String content = Files.readString(filePath);
                        String fileName = filePath.getFileName().toString();
                        int lastDot = fileName.lastIndexOf('.');
                        String identifier = (lastDot > 0) ? fileName.substring(0, lastDot) : fileName;
                        return ArtifactFactory.create(identifier, content, artifactConfig.getType());
                    } catch (IOException e) {
                        logger.error("Error reading file: " + filePath, e);
                        return null;
                    }
                })
                .filter(a -> a != null)
                .collect(Collectors.toSet());
        }
    }

    public String getName() {
        return config.getName();
    }

    public Path getGoldStandardPath() {
        return Paths.get("dataset/" + config.getGoldStandardPath());
    }
}
/* Licensed under MIT 2025. */
package io.github.ardoco.triad.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ardoco.triad.config.ArtifactConfig;
import io.github.ardoco.triad.config.ProjectConfig;

public class PreprocessedProject extends Project {

    private static final Logger logger = LoggerFactory.getLogger(PreprocessedProject.class);

    private Set<Artifact> sourceArtifacts;
    private Set<Artifact> intermediateArtifacts;
    private Set<Artifact> targetArtifacts;

    private final ProjectConfig originalTextConfig;

    public PreprocessedProject(ProjectConfig config) {
        super(config);
        this.originalTextConfig = createOriginalTextConfig();
        try {
            loadAndCombineArtifacts();
        } catch (IOException e) {
            logger.error("Failed to load and filter preprocessed artifacts", e);
            this.sourceArtifacts = new HashSet<>();
            this.intermediateArtifacts = new HashSet<>();
            this.targetArtifacts = new HashSet<>();
        }
    }

    /**
     * Creates a ProjectConfig pointing to the original raw artifact locations.
     * This ensures the base text is correctly extracted before pre-computed biterms are appended.
     */
    private ProjectConfig createOriginalTextConfig() {
        ProjectConfig original = new ProjectConfig();

        ArtifactConfig sourceConfig = new ArtifactConfig();
        sourceConfig.setPath("Dronology/Requirements");
        sourceConfig.setType(ArtifactType.TEXTUAL);
        original.setSource(sourceConfig);

        ArtifactConfig intermediateConfig = new ArtifactConfig();
        intermediateConfig.setPath("Dronology/Design");
        intermediateConfig.setType(ArtifactType.TEXTUAL);
        original.setIntermediate(intermediateConfig);

        ArtifactConfig targetConfig = new ArtifactConfig();
        targetConfig.setPath("Dronology/Code");
        targetConfig.setType(ArtifactType.JAVA_CODE); // Use JAVA_CODE to let the factory parse identifiers/comments.
        original.setTarget(targetConfig);

        return original;
    }

    @Override
    public Set<Artifact> getSourceArtifacts() {
        return this.sourceArtifacts;
    }

    @Override
    public Set<Artifact> getIntermediateArtifacts() {
        return this.intermediateArtifacts;
    }

    @Override
    public Set<Artifact> getTargetArtifacts() {
        return this.targetArtifacts;
    }

    private void loadAndCombineArtifacts() throws IOException {
        logger.info("Starting special loading process to combine original text with pre-processed biterms...");

        // Step 1: Load original artifacts to get their processed text bodies.
        Project originalProject = new Project(this.originalTextConfig);
        Set<Artifact> originalSources = originalProject.getSourceArtifacts();
        Set<Artifact> originalIntermediates = originalProject.getIntermediateArtifacts();
        Set<Artifact> originalTargets = originalProject.getTargetArtifacts();

        logger.info(
                "Loaded originals | REQ: {}  DD: {}  CODE: {}",
                originalSources.size(),
                originalIntermediates.size(),
                originalTargets.size());

        // Step 2: Load the pre-computed biterms and APPEND them to the text body of the original artifacts.
        this.sourceArtifacts =
                combineTextAndBiterms(originalSources, getConfig().getSource());
        this.intermediateArtifacts =
                combineTextAndBiterms(originalIntermediates, getConfig().getIntermediate());
        this.targetArtifacts =
                combineTextAndBiterms(originalTargets, getConfig().getTarget());

        logger.info(
                "After append     | REQ: {}  DD: {}  CODE: {}",
                this.sourceArtifacts.size(),
                this.intermediateArtifacts.size(),
                this.targetArtifacts.size());

        logger.info("Artifact combination complete.");
    }

    private Set<Artifact> combineTextAndBiterms(Set<Artifact> originalArtifacts, ArtifactConfig bitermConfig)
            throws IOException {
        Map<String, Artifact> originalMap =
                originalArtifacts.stream().collect(Collectors.toMap(Artifact::getIdentifier, Function.identity()));

        Path bitermRoot = Paths.get("dataset").resolve(bitermConfig.getPath());

        try (Stream<Path> bitermFiles = Files.walk(bitermRoot)) {
            return bitermFiles
                    .filter(Files::isRegularFile)
                    .map(bitermFilePath -> {
                        String fileName = bitermFilePath.getFileName().toString();
                        String identifier = fileName.substring(0, fileName.lastIndexOf('.'));

                        Artifact originalArtifact = originalMap.get(identifier);
                        if (originalArtifact == null) {
                            logger.warn("No matching original artifact found for biterm file: {}", fileName);
                            return null;
                        }

                        try {
                            String bitermFileContent = Files.readString(bitermFilePath);

                            // Use PrecomputedBitermArtifact and preserve access to original base text via override
                            PrecomputedBitermArtifact precomputedArtifact =
                                    new PrecomputedBitermArtifact(
                                            identifier, bitermFileContent, originalArtifact.getType()) {
                                        @Override
                                        public String getEnrichmentBaseText() {
                                            return originalArtifact.getTextBody();
                                        }
                                    };

                            return precomputedArtifact;
                        } catch (IOException e) {
                            logger.error("Could not read biterm file: {}", bitermFilePath, e);
                            return null;
                        }
                    })
                    .filter(a -> a != null)
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Normalizes pre-computed biterm strings from the original TRIAD format into two lowercase tokens,
     * suitable for appending to the text body for IR model processing.
     */
    private String generateTextFromBiterms(String content) {
        StringBuilder sb = new StringBuilder();
        if (content == null || content.isBlank()) return "";

        Arrays.stream(content.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty() && line.contains(":"))
                .forEach(line -> {
                    try {
                        String[] parts = line.split(":", 2);
                        String raw = parts[0].trim();
                        int weight = Integer.parseInt(parts[1].trim());

                        String[] w = raw.split("\\s+");
                        String t1, t2;
                        if (w.length == 2) {
                            t1 = w[0];
                            t2 = w[1];
                        } else {
                            String norm =
                                    raw.replace('_', ' ').replace('-', ' ').trim();
                            String[] c = norm.split("(?<!^)(?=[A-Z])|\\s+");
                            if (c.length >= 2) {
                                t1 = c[0];
                                t2 = c[1];
                            } else return; // skip malformed
                        }
                        t1 = t1.toLowerCase(java.util.Locale.ROOT);
                        t2 = t2.toLowerCase(java.util.Locale.ROOT);

                        for (int i = 0; i < weight; i++)
                            sb.append(t1).append(' ').append(t2).append(' ');
                    } catch (Exception ignore) {
                        /* skip malformed */
                    }
                });
        return sb.toString().trim();
    }
}

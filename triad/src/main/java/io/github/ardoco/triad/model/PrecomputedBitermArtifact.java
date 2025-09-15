package io.github.ardoco.triad.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * An artifact that holds a pre-computed set of biterms loaded directly from a file.
 * This is used for testing and comparison purposes to bypass the standard text processing
 * and biterm extraction pipeline, allowing the rest of the TRIAD algorithm to be fed
 * with a specific, predetermined set of biterms (e.g., from the original implementation's output).
 */
public class PrecomputedBitermArtifact extends Artifact {

    private final ArtifactType originalType;

    /**
     * Constructs an artifact from pre-computed biterm data.
     * The text body is expected to be the content of a biterm file from the original TRIAD execution,
     * where each line is in the format "biterm:frequency".
     *
     * @param identifier   The artifact's unique identifier.
     * @param bitermFileContent The string content of the pre-computed biterm file.
     * @param originalType The original type of the artifact (e.g., TEXTUAL, JAVA_CODE).
     */
    public PrecomputedBitermArtifact(String identifier, String bitermFileContent, ArtifactType originalType) {
        super(identifier, ""); // Start with an empty text body
        this.originalType = originalType;
        this.biterms = parseBitermsAndReconstructTextBody(bitermFileContent);
    }

    public PrecomputedBitermArtifact(PrecomputedBitermArtifact other) {
        super(other);
        this.originalType = other.originalType;
    }

    /**
     * Parses the string content of a biterm file into a Set of Biterm objects and simultaneously
     * reconstructs the text body by repeating each biterm according to its frequency. This mimics
     * the behavior of the original TRIAD implementation.
     *
     * @param content The file content to parse.
     * @return A Set of Biterm objects.
     */
    private Set<Biterm> parseBitermsAndReconstructTextBody(String content) {
        Set<Biterm> parsedBiterms = new HashSet<>();
        StringBuilder reconstructedBody = new StringBuilder();

        if (content == null || content.isBlank()) {
            return parsedBiterms;
        }

        Arrays.stream(content.split("\\r?\\n"))
              .map(String::trim)
              .filter(line -> !line.isEmpty() && line.contains(":"))
              .forEach(line -> {
                  try {
                      String[] parts = line.split(":", 2);
                      String bitermString = parts[0];
                      int weight = Integer.parseInt(parts[1]);

                      String[] terms = bitermString.split("(?=[A-Z])", 2);
                      if (terms.length == 2) {
                          String term1 = terms[0].toLowerCase();
                          String term2 = terms[1].toLowerCase();
                          Biterm biterm = new Biterm(term1, term2);
                          biterm.setWeight(weight);
                          parsedBiterms.add(biterm);

                          // Reconstruct the text body by appending the biterm string 'weight' times.
                          for (int i = 0; i < weight; i++) {
                              reconstructedBody.append(biterm.toString()).append(" ");
                          }
                      }
                  } catch (Exception e) {
                      // Malformed lines are ignored.
                  }
              });

        // Set the reconstructed text body on the artifact.
        this.textBody = reconstructedBody.toString().trim();
        return parsedBiterms;
    }

    @Override
    public Artifact deepCopy() {
        return new PrecomputedBitermArtifact(this);
    }

    @Override
    public ArtifactType getType() {
        return this.originalType;
    }

    @Override
    protected void preProcessing() {
        // No-op, as biterms are pre-computed and no text processing is needed.
    }

    /**
     * Returns the pre-computed set of biterms directly.
     * This overrides the base implementation to avoid NLP/parsing logic.
     *
     * @return The cached set of biterms.
     */
    @Override
    public Set<Biterm> getBiterms() {
        return this.biterms;
    }
}
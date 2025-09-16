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

                      String[] terms = extractTwoTerms(bitermString);
                      if (terms.length == 2) {
                          String term1 = terms[0].toLowerCase();
                          String term2 = terms[1].toLowerCase();
                          Biterm biterm = new Biterm(term1, term2);
                          biterm.setWeight(weight);
                          parsedBiterms.add(biterm);

                          // Reconstruct the text body by appending the biterm string 'weight' times.
                          for (int i = 0; i < weight; i++) {
                              reconstructedBody.append(term1).append(" ").append(term2).append(" ");
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
    
    /**
     * Extracts two terms from a biterm string. Tries different splitting strategies.
     */
    private String[] extractTwoTerms(String bitermString) {
        // Strategy 1: Split on camelCase if present
        String[] camelCaseSplit = bitermString.split("(?=[A-Z])", 2);
        if (camelCaseSplit.length == 2 && !camelCaseSplit[0].isEmpty() && !camelCaseSplit[1].isEmpty()) {
            return camelCaseSplit;
        }
        
        // Strategy 2: For lowercase strings like "apimonitor", try to find word boundaries
        // This is a simple heuristic - split at roughly the middle for equal-length terms
        if (bitermString.length() >= 6) {
            int mid = bitermString.length() / 2;
            return new String[]{bitermString.substring(0, mid), bitermString.substring(mid)};
        }
        
        // Strategy 3: If very short, treat as single term repeated
        if (bitermString.length() >= 3) {
            int mid = bitermString.length() / 2;
            return new String[]{bitermString.substring(0, mid), bitermString.substring(mid)};
        }
        
        // Fallback: return as single term twice
        return new String[]{bitermString, bitermString};
    }
}
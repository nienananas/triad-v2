/* Licensed under MIT 2025. */
package io.github.ardoco.triad.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import edu.stanford.nlp.util.IdentityHashSet;

import io.github.ardoco.triad.ir.SingleLink;

public class GoldStandard {
    private final Map<String, Set<String>> links = new HashMap<>();

    /**
     * @param goldStandardFile a direct path to the gold-standard file.
     */
    public GoldStandard(Path goldStandardFile) throws IOException {
        if (goldStandardFile == null) {
            throw new IllegalArgumentException("goldStandardFile can not be null");
        }
        if (Files.isDirectory(goldStandardFile)) {
            throw new IllegalArgumentException("goldStandardFile points to a directory: " + goldStandardFile
                    + " (expected a file). Pass the file path directly in the config.");
        }
        if (!Files.exists(goldStandardFile)) {
            throw new IOException("Gold standard file does not exist: " + goldStandardFile);
        }
        loadLinks(goldStandardFile);
    }

    private void loadLinks(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file)) {
            lines.map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .forEach(line -> {
                        // Accept either "SRC TGT" (any whitespace) or "SRC,TGT"
                        String[] parts = line.contains(",") ? line.split("\\s*,\\s*") : line.split("\\s+");

                        if (parts.length >= 2) {
                            String src = parts[0];
                            String tgt = parts[1];
                            links.computeIfAbsent(src, k -> new HashSet<>()).add(tgt);
                        }
                    });
        }
    }

    /** All links as SingleLink objects (weight 1.0). */
    public Set<SingleLink> getLinks() {
        Set<SingleLink> all = new IdentityHashSet<>();
        links.forEach((src, tgts) -> tgts.forEach(t -> all.add(new SingleLink(src, t, 1.0))));
        return all;
    }

    /** True if (source,target) is in the gold standard. */
    public boolean isLink(String source, String target) {
        return links.getOrDefault(source, Collections.emptySet()).contains(target);
    }

    /** All targets considered correct for the given source. */
    public Set<String> getRelevantLinks(String source) {
        return links.getOrDefault(source, Collections.emptySet());
    }

    /** Total number of gold links. */
    public int getTotalRelevantLinks() {
        return links.values().stream().mapToInt(Set::size).sum();
    }
}

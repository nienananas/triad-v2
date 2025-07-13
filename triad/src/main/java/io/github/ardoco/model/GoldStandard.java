/* Licensed under MIT 2025. */
package io.github.ardoco.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

public class GoldStandard {
    private final Map<String, Set<String>> links = new HashMap<>();

    public GoldStandard(Path goldStandardDirectory) throws IOException {
        loadLinks(goldStandardDirectory.resolve("req-code.txt"));
    }

    private void loadLinks(Path path) throws IOException {
        try (Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    links.computeIfAbsent(parts[0], k -> new HashSet<>()).add(parts[1]);
                }
            });
        }
    }

    public boolean isLink(String source, String target) {
        return links.getOrDefault(source, new HashSet<>()).contains(target);
    }

    public Set<String> getRelevantLinks(String source) {
        return links.getOrDefault(source, Collections.emptySet());
    }

    public int getTotalRelevantLinks() {
        return links.values().stream().mapToInt(Set::size).sum();
    }
}

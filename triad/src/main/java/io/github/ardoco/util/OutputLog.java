/* Licensed under MIT 2025. */
package io.github.ardoco.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.ardoco.Biterm.Biterm;
import io.github.ardoco.model.SimilarityMatrix;
import io.github.ardoco.model.SingleLink;

public class OutputLog {

    public static void writeBitermsToFile(String path, Set<? extends Biterm> biterms) throws IOException {
        String content = biterms.stream().map(Biterm::toString).collect(Collectors.joining(System.lineSeparator()));

        Files.writeString(Paths.get(path), content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static void writeSimilarityMatrixToFile(String path, SimilarityMatrix similarityMatrix) throws IOException {
        String content = similarityMatrix.toString();

        Files.writeString(Paths.get(path), content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static void writeTraceabilityLinksToFile(String path, List<SingleLink> links) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("Source,Target,Score\n");
        for (SingleLink link : links) {
            sb.append(String.format(
                    "%s,%s,%.4f\n", link.getSourceArtifactId(), link.getTargetArtifactId(), link.getScore()));
        }
        Files.writeString(
                Paths.get(path), sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}

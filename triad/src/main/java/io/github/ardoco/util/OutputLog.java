package io.github.ardoco.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static void writePrecisionRecallCurveToFile(String path, String approachName, List<Double> precisions) throws IOException {
        Path filePath = Paths.get(path);
        StringBuilder sb = new StringBuilder();

        // If the file doesn't exist, write the header first.
        if (!Files.exists(filePath)) {
            sb.append("Approach,Recall,Precision\n");
        }

        for (int i = 0; i < precisions.size(); i++) {
            double recallLevel = (i + 1) / 20.0; // From 0.05 to 1.00
            double precision = precisions.get(i);
            sb.append(String.format("%s,%.2f,%.4f\n", approachName, recallLevel, precision));
        }

        Files.writeString(filePath, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
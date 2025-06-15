package io.github.ardoco.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.ardoco.Biterm.Biterm;

public class OutputLog {

    public static void writeBitermsToFile(String path, Set<? extends Biterm> biterms) throws IOException {
        String content = biterms.stream()
                                .map(Biterm::toString)
                                .collect(Collectors.joining(System.lineSeparator()));

        Files.writeString(Paths.get(path), content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
} 
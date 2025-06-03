package io.github.ardoco.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TextProcessingUtils {
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList("a", "the"));

    public static List<String> removeStopwords(List<String> words) {
        return words.stream()
            .filter(word -> !STOPWORDS.contains(word.toLowerCase()))
            .toList();
    }
} 
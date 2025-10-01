/* Licensed under MIT 2025. */
package io.github.ardoco.triad.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A general-purpose text processing utility.
 * This class provides a consistent pipeline for cleaning, tokenizing, normalizing,
 * and stemming both natural language text and code identifiers. It is designed to be
 * stateless and free of project-specific logic.
 */
public class TextProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TextProcessor.class);
    private static final Set<String> STOPWORDS = new HashSet<>();

    static {
        try (InputStream is = TextProcessor.class.getResourceAsStream("/stopwords.txt")) {
            if (is == null) {
                logger.warn("stopwords.txt not found. No stopwords will be removed.");
            } else {
                new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                        .lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .forEach(STOPWORDS::add);
            }
        } catch (IOException e) {
            logger.error("Failed to load stopwords.txt.", e);
        }
    }

    /**
     * Processes a block of text through the full pipeline.
     * This is the main entry point for processing artifact content.
     */
    public static String processText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = cleanCharacters(text);
        return Arrays.stream(cleaned.split("\\s+"))
                .map(TextProcessor::processWord)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
    }

    /**
     * Processes a code identifier through the full pipeline.
     * This method serves as an alias for processText, as the core pipeline
     * is general enough for both text and identifiers.
     */
    public static String processIdentifier(String text) {
        return processText(text);
    }

    /**
     * Applies the core processing pipeline to a single, already-tokenized word.
     * This method is ideal for processing terms from a dependency parser.
     */
    public static String processWord(String word) {
        if (word == null || word.isBlank()) {
            return "";
        }
        // Handle pluralized acronyms like "UAVs" before other processing
        if (word.endsWith("s") && word.length() > 1 && Character.isUpperCase(word.charAt(word.length() - 2))) {
            word = word.substring(0, word.length() - 1);
        }

        String camelCaseSplit = splitCamelCase(word);
        String snakeCaseSplit = splitSnakeCase(camelCaseSplit);
        String lowercased = snakeCaseSplit.toLowerCase(Locale.ROOT);
        String stopwordsRemoved1 = removeStopwords(lowercased);
        String stemmed = stemText(stopwordsRemoved1);
        String stopwordsRemoved2 = removeStopwords(stemmed);
        return lengthFilter(stopwordsRemoved2, 2).trim();
    }

    /**
     * Performs general character cleaning, removing non-alphabetic characters
     * and handling common separators like hyphens.
     */
    private static String cleanCharacters(String input) {
        // Replace hyphens with spaces to split words like "content-type"
        String hyphenAsSpace = input.replace("-", " ");
        String cleaned = hyphenAsSpace
                .replaceAll("[^a-zA-Z]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        // Special handling for acronyms must happen before lowercasing.
        return cleaned.replaceAll("\\bUAVs\\b", "UAV");
    }

    /**
     * Splits a string based on camel case conventions.
     */
    private static String splitCamelCase(String s) {
        String acronymProcessed = splitAcronyms(s);
        return acronymProcessed
                .replaceAll(
                        String.format(
                                "%s|%s|%s",
                                "(?<=[A-Z])(?=[A-Z][a-z])", "(?<=[^A-Z])(?=[A-Z])", "(?<=[A-Za-z])(?=[^A-Za-z])"),
                        " ")
                .replaceAll("  ", " ");
    }

    /**
     * A helper method to correctly split pluralized acronyms (e.g., "UAVs" -> "UAV s").
     */
    private static String splitAcronyms(String input) {
        List<String> words = new ArrayList<>();
        for (String word : input.split("\\s+")) {
            if (word.isEmpty()) continue;
            StringBuilder currentWord = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                char currentChar = word.charAt(i);
                if (i > 0 && Character.isUpperCase(word.charAt(i - 1)) && currentChar == 's') {
                    boolean isEndOfWord = (i == word.length() - 1);
                    boolean isFollowedByUppercase = !isEndOfWord && Character.isUpperCase(word.charAt(i + 1));
                    if (isEndOfWord || isFollowedByUppercase) {
                        words.add(currentWord.toString());
                        words.add("s");
                        currentWord.setLength(0);
                        continue;
                    }
                }
                currentWord.append(currentChar);
            }
            if (currentWord.length() > 0) {
                words.add(currentWord.toString());
            }
        }
        return String.join(" ", words);
    }

    /**
     * Splits a string based on snake case conventions.
     */
    private static String splitSnakeCase(String s) {
        return s.replace("_", " ");
    }

    /**
     * Removes words shorter than a given minimum length.
     */
    private static String lengthFilter(String input, int minLength) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return Arrays.stream(input.split("\\s+"))
                .filter(word -> word.length() >= minLength)
                .collect(Collectors.joining(" "));
    }

    /**
     * Removes common English stopwords from a string.
     */
    private static String removeStopwords(String text) {
        if (STOPWORDS.isEmpty() || text == null || text.isBlank()) return text;
        return Arrays.stream(text.split("\\s+"))
                .filter(w -> !STOPWORDS.contains(w))
                .collect(Collectors.joining(" "));
    }

    /**
     * Stems all words in a string using the Snowball English stemmer.
     */
    private static String stemText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return Arrays.stream(text.split("\\s+")).map(Stemmer::stem).collect(Collectors.joining(" "));
    }
}

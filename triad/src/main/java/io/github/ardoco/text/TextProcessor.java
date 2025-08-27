package io.github.ardoco.text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    
    public static boolean isStopword(String word) {
        return STOPWORDS.contains(word.toLowerCase(Locale.ROOT));
    }

    /**
     * Processes natural language text with a standard pipeline.
     */
    public static String processText(String text) {
        String cleaned = cleanCharacters(text);
        String camelCaseSplit = splitCamelCase(cleaned);
        String lengthFiltered = lengthFilter(camelCaseSplit, 2);
        String lowercased = lengthFiltered.toLowerCase(Locale.ROOT);
        String stopwordsRemoved1 = removeStopwords(lowercased);
        String stemmed = stemText(stopwordsRemoved1);
        String stopwordsRemoved2 = removeStopwords(stemmed);
        return stopwordsRemoved2.trim();
    }
    
    /**
     * Processes code identifiers by splitting camelCase and snake_case.
     */
     public static String processIdentifier(String text) {
        String cleaned = cleanCharacters(text);
        String camelCaseSplit = splitCamelCase(cleaned);
        String snakeCaseSplit = splitSnakeCase(camelCaseSplit);
        String lengthFiltered = lengthFilter(snakeCaseSplit, 2);
        String lowercased = lengthFiltered.toLowerCase(Locale.ROOT);
        String stopwordsRemoved = removeStopwords(lowercased);
        String stemmed = stemText(stopwordsRemoved);
        return removeStopwords(stemmed).trim();
    }


    private static String cleanCharacters(String input) {
        Pattern pattern = Pattern.compile("[a-zA-Z]+");
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            sb.append(matcher.group()).append(" ");
        }
        return sb.toString().trim();
    }

    /**
     * A specific processing pipeline for individual terms after dependency parsing.
     */
    public static String processTermOriginal(String term) {
        if (term == null || term.isBlank()) return "";
        String cleaned = cleanCharacters(term);
        String camelCaseSplit = splitCamelCase(cleaned);
        String lemmatized = StanfordLemmatizer.lemmatize(camelCaseSplit);
        String lengthFiltered = lengthFilter(lemmatized, 3);
        String lowercased = lengthFiltered.toLowerCase(Locale.ROOT);
        String stopwordsRemoved = removeStopwords(lowercased);
        
        // Handle special case, copied from original codebase
        if ("sent".equals(stopwordsRemoved.trim())) {
            return "send";
        }
        return stopwordsRemoved.trim();
    }

    private static String splitCamelCase(String s) {
        return s.replaceAll(
            String.format("%s|%s|%s",
                "(?<=[A-Z])(?=[A-Z][a-z])",
                "(?<=[^A-Z])(?=[A-Z])",
                "(?<=[A-Za-z])(?=[^A-Za-z])"
            ),
            " "
        ).replaceAll("  ", " ");
    }

    private static String splitSnakeCase(String s) {
        return s.replace("_", " ");
    }
    
    private static String lengthFilter(String input, int minLength) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return Arrays.stream(input.split("\\s+"))
                .filter(word -> word.length() >= minLength)
                .collect(Collectors.joining(" "));
    }

    private static String removeStopwords(String text) {
        if (STOPWORDS.isEmpty() || text == null || text.isBlank()) {
            return text;
        }
        return Arrays.stream(text.split("\\s+"))
                .filter(word -> !STOPWORDS.contains(word))
                .collect(Collectors.joining(" "));
    }

    private static String stemText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return Arrays.stream(text.split("\\s+"))
                .map(Stemmer::stem)
                .collect(Collectors.joining(" "));
    }
}
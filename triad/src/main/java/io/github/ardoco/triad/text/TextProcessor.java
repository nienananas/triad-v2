package io.github.ardoco.triad.text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Processes natural language text with a standard pipeline, including removal of Dronology-specific headers.
     */
    public static String processText(String text) {
        String cleaned = cleanRawText(text); // This removes headers
        return Arrays.stream(cleaned.split("\\s+"))
                .map(TextProcessor::processWord)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
    }

    /**
     * Processes code identifiers by splitting camelCase and snake_case, stemming, and removing stopwords.
     */
     public static String processIdentifier(String text) {
        // Identifiers don't have headers, but they do have special characters
        String cleaned = cleanCharacters(text);
        return Arrays.stream(cleaned.split("\\s+"))
                .map(TextProcessor::processWord)
                .collect(Collectors.joining(" "))
                .trim();
    }

    /**
     * Applies the core processing pipeline to a single word/token.
     */
    private static String processWord(String word) {
        String processedWord = word;
        // Handle pluralized acronyms before other processing
        if (processedWord.endsWith("s") && processedWord.length() > 1 && Character.isUpperCase(processedWord.charAt(processedWord.length() - 2))) {
            processedWord = processedWord.substring(0, processedWord.length() - 1);
        }

        String camelCaseSplit = splitCamelCase(processedWord);
        String snakeCaseSplit = splitSnakeCase(camelCaseSplit);
        String lowercased = snakeCaseSplit.toLowerCase(Locale.ROOT);
        String stopwordsRemoved1 = removeStopwords(lowercased);
        String stemmed = stemText(stopwordsRemoved1);
        String stopwordsRemoved2 = removeStopwords(stemmed);
        return lengthFilter(stopwordsRemoved2, 2).trim();
    }
    
    /**
     * Cleans raw text from artifacts by removing Dronology headers and special characters.
     */
    private static String cleanRawText(String input) {
        String noHeaders = input.replaceAll("(?i)\\[(SUMMARY|DESCRIPTION)\\]", " ");
        return cleanCharacters(noHeaders);
    }

    /**
     * Generic character cleaning: replaces hyphens, splits on other non-alphanumerics.
     */
    private static String cleanCharacters(String input) {
        // This is the correct logic: remove hyphens to merge words, then clean.
        String noHyphens = input.replace("-", "");
        String cleaned = noHyphens.replaceAll("[^a-zA-Z]", " ").replaceAll("\\s+", " ").trim();
        // Special handling for acronyms must happen before lowercasing.
        return cleaned.replaceAll("\\bUAVs\\b", "UAV");
    }

    /**
     * Processes a single term using a pipeline that mirrors the original TRIAD implementation.
     * This is used after dependency parsing to ensure compatibility with the original biterm extraction logic.
     */
    public static String processTermOriginal(String term) {
        if (term == null || term.isBlank()) return "";

        // This pipeline directly replicates the logic from the original TRIAD's StanfordNlpUtil.doTextProcess
        String cleaned = term.replaceAll("[^a-zA-Z]+", " ").trim();
        String camelCaseSplit = splitCamelCase(cleaned);
        String lemmatized = StanfordLemmatizer.lemmatize(camelCaseSplit);
        String lengthFiltered = lengthFilter(lemmatized, 3);
        String lowercased = lengthFiltered.toLowerCase(Locale.ROOT);
        // ORIGINAL ORDER: Stopword removal happens BEFORE stemming.
        String stopwordsRemoved = removeStopwords(lowercased); 
        String stemmed = stemText(stopwordsRemoved);
        
        // Retained for compatibility with the original implementation.
        // TODO: delete
        if ("sent".equals(stemmed.trim())) {
            return "send";
        }
        
        // Final check for length, as stemming can shorten words
        return lengthFilter(stemmed, 3).trim();
    }

    private static String splitCamelCase(String s) {
        // This is a two-step process. First, we handle a specific acronym pluralization
        // pattern (e.g., "UAVs") that the standard regex doesn't handle well.
        String acronymProcessed = splitAcronyms(s);

        // Then, we apply a standard camel case splitting regex to the result.
        return acronymProcessed.replaceAll(
            String.format("%s|%s|%s",
                "(?<=[A-Z])(?=[A-Z][a-z])",
                "(?<=[^A-Z])(?=[A-Z])",
                "(?<=[A-Za-z])(?=[^A-Za-z])"
            ),
            " "
        ).replaceAll("  ", " ");
    }
    
    /**
     * Replicates a specific acronym handling routine from the original TRIAD's CamelCase.java.
     * The idea is to correctly split pluralized acronyms like "UAVs" into "UAV s".
     *
     * @param input The string to process.
     * @return The string with the specific acronym pattern split.
     */
    private static String splitAcronyms(String input) {
        List<String> words = new ArrayList<>();
        for (String word : input.split("\\s+")) {
            if (word.isEmpty()) continue;

            StringBuilder currentWord = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                char currentChar = word.charAt(i);

                // Check for "end-of-acronym 's'" pattern
                if (i > 0 && Character.isUpperCase(word.charAt(i - 1)) && currentChar == 's') {
                    boolean isEndOfWord = (i == word.length() - 1);
                    boolean isFollowedByUppercase = !isEndOfWord && Character.isUpperCase(word.charAt(i + 1));
                    
                    if (isEndOfWord || isFollowedByUppercase) {
                        words.add(currentWord.toString());
                        words.add("s");
                        currentWord.setLength(0); // Reset
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
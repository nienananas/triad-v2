/* Licensed under MIT 2025. */
package io.github.ardoco.triad.text;

import java.util.Locale;

import org.tartarus.snowball.ext.EnglishStemmer;

public class Stemmer {
    private static final EnglishStemmer stemmer = new EnglishStemmer();

    /**
     * Stems a single term to its root form using the Snowball English Stemmer.
     *
     * @param term The term to stem.
     * @return The stemmed term in lowercase.
     */
    public static String stem(String term) {
        if (term == null) {
            return null;
        }
        stemmer.setCurrent(term.toLowerCase(Locale.ROOT));
        stemmer.stem();
        return stemmer.getCurrent();
    }
}

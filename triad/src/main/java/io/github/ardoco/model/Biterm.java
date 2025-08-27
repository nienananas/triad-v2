package io.github.ardoco.model;

import java.util.Objects;

public class Biterm implements Comparable<Biterm> {
    private final String term1;
    private final String term2;
    private final String canonicalRepresentation;
    private int weight = 1;

    public Biterm(String term1, String term2) {
        // Canonicalize by sorting alphabetically to treat (a,b) and (b,a) as the same term.
        if (term1.compareTo(term2) < 0) {
            this.term1 = term1;
            this.term2 = term2;
        } else {
            this.term1 = term2;
            this.term2 = term1;
        }
        this.canonicalRepresentation = createCanonicalRepresentation(this.term1, this.term2);
    }

    /**
     * Creates the canonical string representation for a biterm, e.g., "create" + "Request" -> "createRequest".
     */
    private static String createCanonicalRepresentation(String t1, String t2) {
        if (t2 == null || t2.isEmpty()) return t1;
        char[] chars = t2.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return t1 + new String(chars);
    }

    public String getFirstTerm() { return term1; }
    public String getSecondTerm() { return term2; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public void incrementWeight(int amount) { this.weight += amount; }

    @Override
    public String toString() {
        return this.canonicalRepresentation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Biterm)) return false;
        Biterm biterm = (Biterm) o;
        return canonicalRepresentation.equals(biterm.canonicalRepresentation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(canonicalRepresentation);
    }

    @Override
    public int compareTo(Biterm o) {
        return this.canonicalRepresentation.compareTo(o.canonicalRepresentation);
    }
}
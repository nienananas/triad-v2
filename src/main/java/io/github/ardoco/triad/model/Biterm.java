package io.github.ardoco.triad.model;

import java.util.Objects;

public class Biterm implements Comparable<Biterm> {
    private final String term1;
    private final String term2;
    private final String stringRepresentation; 
    private int weight = 1;

    public Biterm(String term1, String term2) {
        this.term1 = term1;
        this.term2 = term2;
        this.stringRepresentation = createStringRepresentation(this.term1, this.term2);
    }

    /**
     * Creates the canonical string representation, e.g., "flight" and "pattern" becomes "flightPattern".
     */
    private static String createStringRepresentation(String t1, String t2) {
        if (t2 == null || t2.isEmpty()) return t1;
        if (t1 == null || t1.isEmpty()) return t2;
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
    public String toString() { return this.stringRepresentation; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Biterm biterm)) return false;
        return stringRepresentation.equals(biterm.stringRepresentation);
    }

    @Override
    public int hashCode() { return Objects.hash(stringRepresentation); }

    @Override
    public int compareTo(Biterm o) { return this.stringRepresentation.compareTo(o.stringRepresentation); }
}
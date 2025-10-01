package io.github.ardoco.triad.model;

import java.util.Objects;

public class ConsensualBiterm extends Biterm {
    private String source;
    private String destination;

    public ConsensualBiterm(Biterm biterm, String source, String destination) {
        super(biterm.getFirstTerm(), biterm.getSecondTerm());
        this.source = source;
        this.destination = destination;
    }

    @Override
    public String toString() {
        return super.toString() + " [" + source + " - " + destination + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsensualBiterm that)) return false;
        if (!super.equals(o)) return false;
        return source.equals(that.source) && destination.equals(that.destination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, destination);
    }
}
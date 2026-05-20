package org.investpro.utils;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Container to ease passing around a tuple of two objects. This object provides
 * a sensible
 * implementation of equals(), returning true if equals() is true on each of the
 * contained
 * objects.
 */
public record SymmetricPair<F, S>(F first, S second) {
    /**
     * Constructor for a Pair.
     *
     * @param first  the first object in the Pair
     * @param second the second object in the pair
     */
    public SymmetricPair {
    }

    /**
     * Convenience method for creating an appropriately typed pair.
     *
     * @param a the first object in the Pair
     * @param b the second object in the pair
     * @return a Pair that is templatized with the types of a and b
     */
    public static <A, B> SymmetricPair<A, B> of(A a, B b) {
        return new SymmetricPair<>(a, b);
    }

    /**
     * Checks the two objects for equality by delegating to their respective
     * {@link Object#equals(Object)} methods.
     *
     * @param object the {@link SymmetricPair} to which this one is to be checked
     *               for equality
     * @return true if the underlying objects of the Pair are both considered
     *         equal
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof SymmetricPair<?, ?>(Object first1, Object second1))) {
            return false;
        }

        return (Objects.equals(this.first, first1) && Objects.equals(this.second, second1)) ||
                (Objects.equals(this.second, first1) && Objects.equals(this.first, second1));

    }

    /**
     * Compute a hash code using the hash codes of the underlying objects
     *
     * @return a hashCode of the Pair
     */
    @Override
    public int hashCode() {
        return (first == null ? 0 : first.hashCode()) ^ (second == null ? 0 : second.hashCode());
    }

    @Override
    public @NotNull String toString() {
        return "SymmetricPair [%s, %s]".formatted(first, second);
    }

}

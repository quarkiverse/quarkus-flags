package io.quarkiverse.flags;

import java.util.List;
import java.util.Optional;

/**
 * Represents a central point to access feature flags.
 * <p>
 * A flag from a provider with higher priority takes precedence and overrides flags with the same {@link Flag#feature()}
 * from providers with lower priority.
 *
 * @see Flag
 */
public interface Flags {

    /**
     * @param feature
     * @return the flag for the given feature
     */
    Optional<Flag> find(String feature);

    /**
     * @return an immutable list of feature flags
     */
    List<Flag> findAll();

}

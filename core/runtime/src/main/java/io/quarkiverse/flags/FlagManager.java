package io.quarkiverse.flags;

import java.util.Optional;
import java.util.Set;

/**
 * A feature flag manager.
 */
public interface FlagManager {

    /**
     * @param feature
     * @return the flag for the specific feature
     */
    Optional<Flag> getFlag(String feature);

    /**
     * Collects all flags from all providers.
     * <p>
     * A flag from a provider with higher priority takes precedence and overrides flags with the same {@link Flag#feature()}
     * from providers with lower priority.
     *
     * @return an immutable set of feature flags
     */
    Set<Flag> getFlags();

}

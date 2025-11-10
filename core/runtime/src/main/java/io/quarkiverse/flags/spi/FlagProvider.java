package io.quarkiverse.flags.spi;

import io.quarkiverse.flags.Flag;

public interface FlagProvider {

    int DEFAULT_PRIORITY = 1;

    /**
     * The result must not contain flags with duplicate feature names.
     * <p>
     * A flag from a provider with higher priority takes precedence and overrides flags with the same {@link Flag#feature()}
     * from providers with lower priority.
     *
     * @return the flags
     */
    Iterable<Flag> getFlags();

    /**
     * The priority is reflected when the system collects all flags from all providers.
     *
     * @return the priority
     */
    default int getPriority() {
        return DEFAULT_PRIORITY;
    }

}

package io.quarkiverse.flags;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.quarkiverse.flags.spi.FlagProvider;

/**
 * Provides access to all flags from all providers.
 * <p>
 * A flag from a provider with higher priority takes precedence and overrides flags with the same {@link Flag#feature()}
 * from providers with lower priority.
 *
 * @see Flag
 * @see FlagProvider
 */
public interface Flags extends Iterable<Flag> {

    /**
     * @return an immutable list of flags
     */
    default List<Flag> asList() {
        return stream().toList();
    }

    /**
     * @param feature
     * @return the flag for the given feature
     */
    Optional<Flag> find(String feature);

    /**
     * @return the stream of flags
     */
    Stream<Flag> stream();

}

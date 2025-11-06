package io.quarkiverse.flags.spi;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.FlagManager;

public interface FlagProvider extends Comparable<FlagProvider> {

    int DEFAULT_PRIORITY = 1;

    /**
     * @return the flags
     */
    Iterable<Flag> getFlags();

    /**
     * The priority is reflected when collecting all flags in {@link FlagManager#getFlags()}.
     *
     * @return the priority
     */
    default int getPriority() {
        return DEFAULT_PRIORITY;
    }

    @Override
    default int compareTo(FlagProvider other) {
        return Integer.compare(other.getPriority(), getPriority());
    }

}

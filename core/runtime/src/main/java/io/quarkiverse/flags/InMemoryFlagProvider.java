package io.quarkiverse.flags;

import java.util.Map;
import java.util.function.Function;

import io.quarkiverse.flags.Flag.ComputationContext;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkiverse.flags.spi.ImmutableBooleanValue;
import io.smallrye.mutiny.Uni;

/**
 * An in-memory feature flag provider can be used to add/remove a feature flag.
 */
public interface InMemoryFlagProvider extends FlagProvider {

    /**
     * @param feature
     * @return a new definition
     * @see FlagAdded
     */
    FlagDefinition newFlag(String feature);

    /**
     * @param feature
     * @return the removed flag, or {@code null}
     * @see FlagRemoved
     */
    Flag removeFlag(String feature);

    /**
     * A CDI event that is fired synchronously when a new feature flag is added to the system.
     */
    record FlagAdded(Flag flag) {
    }

    /**
     * A CDI event that is fired synchronously when a feature flag is removed from the system.
     */
    record FlagRemoved(Flag flag) {
    }

    interface FlagDefinition {

        default FlagDefinition setEnabled(boolean value) {
            Flag.Value val = ImmutableBooleanValue.from(value);
            return setCompute(cc -> val);
        }

        default FlagDefinition setCompute(Function<ComputationContext, Flag.Value> fun) {
            return setComputeAsync(cc -> Uni.createFrom().item(fun.apply(cc)));
        }

        FlagDefinition setComputeAsync(Function<ComputationContext, Uni<Flag.Value>> fun);

        FlagDefinition setMetadata(Map<String, String> metadata);

        Flag register();

    }

}

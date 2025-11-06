package io.quarkiverse.flags;

import java.util.function.Function;

import io.quarkiverse.flags.Flag.ComputationContext;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkiverse.flags.spi.ImmutableBooleanState;
import io.smallrye.mutiny.Uni;

/**
 * An in-memory feature flag provider can be used to add/remove a feature flag.
 */
public interface InMemoryFlagProvider extends FlagProvider {

    /**
     * @param feature
     * @return a new definition
     */
    FlagDefinition newFlag(String feature);

    /**
     * @param feature
     * @return the removed flag, or {@code null}
     */
    Flag removeFlag(String feature);

    interface FlagDefinition {

        default FlagDefinition setEnabled(boolean value) {
            return setCompute(cc -> ImmutableBooleanState.from(value));
        }

        default FlagDefinition setCompute(Function<ComputationContext, Flag.State> fun) {
            return setComputeAsync(cc -> Uni.createFrom().item(fun.apply(cc)));
        }

        FlagDefinition setComputeAsync(Function<ComputationContext, Uni<Flag.State>> fun);

        Flag add();

    }

}

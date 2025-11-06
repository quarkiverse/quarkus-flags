package io.quarkiverse.flags.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flag.ComputationContext;
import io.quarkiverse.flags.Flag.State;
import io.quarkiverse.flags.FlagAdded;
import io.quarkiverse.flags.FlagRemoved;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkiverse.flags.spi.FlagProvider;
import io.smallrye.mutiny.Uni;

@Singleton
public class InMemoryFlagProviderImpl implements InMemoryFlagProvider {

    private final ConcurrentMap<String, Flag> flags = new ConcurrentHashMap<>();

    @Inject
    Event<FlagAdded> flagAdded;

    @Inject
    Event<FlagRemoved> flagRemoved;

    @Override
    public int getPriority() {
        return FlagProvider.DEFAULT_PRIORITY + 3;
    }

    @Override
    public Iterable<Flag> getFlags() {
        return flags.values();
    }

    @Override
    public FlagDefinition newFlag(String feature) {
        return new FlagDefinitionImpl(feature);
    }

    @Override
    public Flag removeFlag(String feature) {
        Flag removed = flags.remove(feature);
        if (removed != null) {
            flagRemoved.fire(new FlagRemoved(removed));
        }
        return removed;
    }

    class FlagDefinitionImpl implements FlagDefinition {

        private final String feature;

        private Function<ComputationContext, Uni<State>> fun;

        FlagDefinitionImpl(String feature) {
            this.feature = feature;
        }

        @Override
        public FlagDefinition setComputeAsync(Function<ComputationContext, Uni<State>> fun) {
            this.fun = fun;
            return this;
        }

        @Override
        public Flag add() {
            Flag newFlag = new InMemoryFlag(feature, fun);
            Flag existing = flags.putIfAbsent(feature, newFlag);
            if (existing == null) {
                flagAdded.fire(new FlagAdded(newFlag));
                return newFlag;
            }
            return null;
        }

    }

    class InMemoryFlag implements Flag {

        private final String feature;

        private final Function<ComputationContext, Uni<State>> fun;

        InMemoryFlag(String feature, Function<ComputationContext, Uni<State>> fun) {
            this.feature = feature;
            this.fun = fun;
        }

        @Override
        public String feature() {
            return feature;
        }

        @Override
        public Uni<State> compute(ComputationContext context) {
            return fun.apply(context);
        }

    }

}

package io.quarkiverse.flags.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flag.ComputationContext;
import io.quarkiverse.flags.Flag.Value;
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

        private Map<String, String> metadata = Map.of();

        private Function<ComputationContext, Uni<Value>> fun;

        FlagDefinitionImpl(String feature) {
            this.feature = feature;
        }

        @Override
        public FlagDefinition setComputeAsync(Function<ComputationContext, Uni<Value>> fun) {
            this.fun = fun;
            return this;
        }

        @Override
        public FlagDefinition setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        @Override
        public Flag register() {
            Flag newFlag = new InMemoryFlag(feature, metadata, fun);
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

        private final Map<String, String> metadata;

        private final Function<ComputationContext, Uni<Value>> fun;

        InMemoryFlag(String feature, Map<String, String> metadata, Function<ComputationContext, Uni<Value>> fun) {
            this.feature = feature;
            this.metadata = metadata;
            this.fun = fun;
        }

        @Override
        public String feature() {
            return feature;
        }

        @Override
        public Map<String, String> metadata() {
            return metadata;
        }

        @Override
        public Uni<Value> compute(ComputationContext context) {
            return fun.apply(context);
        }

    }

}

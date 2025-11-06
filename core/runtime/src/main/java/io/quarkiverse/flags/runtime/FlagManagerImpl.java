package io.quarkiverse.flags.runtime;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.flags.Feature;
import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.FlagManager;
import io.quarkiverse.flags.spi.FlagInterceptor;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkus.arc.All;
import io.quarkus.runtime.Startup;
import io.smallrye.mutiny.Uni;

@Startup
@Singleton
public class FlagManagerImpl implements FlagManager {

    private static final Logger LOG = Logger.getLogger(FlagManagerImpl.class);

    private final List<FlagProvider> providers;

    private final List<FlagInterceptor> interceptors;

    private FlagManagerImpl(@All List<FlagProvider> providers, @All List<FlagInterceptor> interceptors) {
        List<FlagProvider> sortedProviders = new ArrayList<>();
        int lastPriority = Integer.MAX_VALUE;
        for (FlagProvider provider : providers.stream().sorted().toList()) {
            if (provider.getPriority() < lastPriority) {
                sortedProviders.add(provider);
            } else {
                throw new IllegalStateException(
                        "Multiple feature flag providers with the same priority detected: "
                                + providers.stream().map(p -> "\n\t-" + p.getClass().getName() + ":" + p.getPriority())
                                        .collect(Collectors.joining()));
            }
            lastPriority = provider.getPriority();
        }
        this.providers = List.copyOf(sortedProviders);
        this.interceptors = interceptors;
    }

    @Override
    public Optional<Flag> getFlag(String feature) {
        return getFlags().stream()
                .filter(f -> f.feature().equals(feature))
                .findFirst();
    }

    @Override
    public Set<Flag> getFlags() {
        Set<Flag> ret = new HashSet<>();
        for (FlagProvider provider : providers) {
            for (Flag flag : provider.getFlags()) {
                if (!ret.add(new InterceptedFlag(flag))) {
                    LOG.debugf(
                            "Flag with feature %s from provider %s is ignored: a flag with the same feature is declared by a provider with higher priority",
                            flag.feature(), provider.getClass().getName());
                }
            }
        }
        return ret;
    }

    @Feature("")
    @Produces
    Flag produceFlag(InjectionPoint injectionPoint) {
        Feature feature = null;
        for (Annotation qualifier : injectionPoint.getQualifiers()) {
            if (qualifier.annotationType().equals(Feature.class)) {
                feature = (Feature) qualifier;
            }
        }
        if (feature != null) {
            return getFlag(feature.value()).orElse(null);
        }
        return null;
    }

    @Feature("")
    @Produces
    Optional<Flag> produceOptionalFlag(InjectionPoint injectionPoint) {
        Feature feature = null;
        for (Annotation qualifier : injectionPoint.getQualifiers()) {
            if (qualifier.annotationType().equals(Feature.class)) {
                feature = (Feature) qualifier;
            }
        }
        if (feature != null) {
            return getFlag(feature.value());
        }
        return Optional.empty();
    }

    class InterceptedFlag implements Flag {

        private final Flag delegate;

        InterceptedFlag(Flag delegate) {
            this.delegate = delegate;
        }

        @Override
        public String feature() {
            return delegate.feature();
        }

        @Override
        public Uni<State> compute(ComputationContext computationContext) {
            Uni<State> state = delegate.compute();
            if (!interceptors.isEmpty()) {
                return applyNextInterceptor(state, computationContext, interceptors.iterator());
            }
            return state;
        }

        Uni<State> applyNextInterceptor(Uni<State> state, ComputationContext computationContext, Iterator<FlagInterceptor> it) {
            if (it.hasNext()) {
                return state.chain(s -> {
                    FlagInterceptor interceptor = it.next();
                    Uni<State> nextState = interceptor.afterCompute(delegate, s, computationContext);
                    return applyNextInterceptor(nextState, computationContext, it);
                });
            }
            return state;
        }

        @Override
        public int hashCode() {
            return delegate.feature().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof Flag))
                return false;
            Flag other = (Flag) obj;
            return Objects.equals(delegate.feature(), other.feature());
        }

    }

}

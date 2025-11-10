package io.quarkiverse.flags.runtime;

import static java.util.stream.Collectors.toMap;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;

import org.jboss.logging.Logger;

import io.quarkiverse.flags.Feature;
import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.spi.FlagEvaluator;
import io.quarkiverse.flags.spi.FlagManager;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkus.arc.All;
import io.quarkus.runtime.Startup;
import io.smallrye.mutiny.Uni;

@Startup
@ApplicationScoped
public class FlagManagerImpl implements FlagManager {

    private static final Logger LOG = Logger.getLogger(FlagManagerImpl.class);

    private final List<FlagProvider> providers;

    private final Map<String, FlagEvaluator> evaluators;

    private FlagManagerImpl(@All List<FlagProvider> providers,
            @All List<FlagEvaluator> evaluators) {
        List<FlagProvider> sortedProviders = new ArrayList<>();
        int lastPriority = Integer.MAX_VALUE;
        for (FlagProvider provider : providers.stream().sorted(new Comparator<FlagProvider>() {
            @Override
            public int compare(FlagProvider o1, FlagProvider o2) {
                return Integer.compare(o2.getPriority(), o1.getPriority());
            }
        }).toList()) {
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
        this.evaluators = evaluators.stream().collect(toMap(FlagEvaluator::id, Function.identity()));
    }

    @Override
    public List<Flag> findAll() {
        return List.copyOf(getFlags());
    }

    @Override
    public Optional<Flag> find(String feature) {
        return getFlags().stream()
                .filter(f -> f.feature().equals(feature))
                .findFirst();
    }

    Set<Flag> getFlags() {
        Set<Flag> ret = new HashSet<>();
        for (FlagProvider provider : providers) {
            for (Flag flag : provider.getFlags()) {
                if (!ret.add(new DelegatingFlag(flag))) {
                    LOG.debugf(
                            "Flag with feature %s from provider %s is ignored: a flag with the same feature is declared by a provider with higher priority",
                            flag.feature(), provider.getClass().getName());
                }
            }
        }
        return ret;
    }

    @Override
    public Optional<FlagEvaluator> getEvaluator(String id) {
        FlagEvaluator evaluator = evaluators.get(id);
        return Optional.ofNullable(evaluator);
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
            return find(feature.value()).orElse(null);
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
            return find(feature.value());
        }
        return Optional.empty();
    }

    class DelegatingFlag implements Flag {

        private final Flag delegate;

        DelegatingFlag(Flag delegate) {
            this.delegate = delegate;
        }

        @Override
        public String feature() {
            return delegate.feature();
        }

        @Override
        public Map<String, String> metadata() {
            return delegate.metadata();
        }

        @Override
        public Uni<Value> compute(ComputationContext computationContext) {
            return delegate.compute(computationContext);
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

        @Override
        public String toString() {
            return "DelegatingFlag [delegate=" + delegate + "]";
        }

    }

}

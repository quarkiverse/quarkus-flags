package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Decorated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

public class FlagProviderCachingDecoratorTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(MyFlagProvider.class, CachingDecorator.class));

    @Inject
    Flags flags;

    @CacheName("quarkus.flags")
    Cache cache;

    @Inject
    InMemoryFlagProvider inMemory;

    @Test
    public void testFlags() {
        // This triggers invocation of FlagProvider#getFlags() for all providers
        assertEquals(1, flags.getInt("my-flag"));
        assertEquals(1, flags.getInt("my-flag"));
        assertThrows(NoSuchElementException.class, () -> flags.getInt("other-flag"));

        cache.invalidateAll().await().indefinitely();
        inMemory.addFlag(Flag.builder("other-flag").setInt(1));

        // Cache was invalidated
        // Trigger invocation of FlagProvider#getFlags() for all providers
        assertEquals(2, flags.getInt("my-flag"));
        assertEquals(2, flags.getInt("my-flag"));
        assertEquals(2, flags.getInt("my-flag"));
        assertEquals(1, flags.getInt("other-flag"));

        inMemory.removeFlag("other-flag");

        // The flag does not exist but it's cached
        assertEquals(1, flags.getInt("other-flag"));

        cache.invalidateAll().await().indefinitely();
        assertThrows(NoSuchElementException.class, () -> flags.getInt("other-flag"));
    }

    @Identifier("my-flag-provider")
    @Singleton
    public static class MyFlagProvider implements FlagProvider {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Uni<Collection<Flag>> getFlags() {
            return Uni.createFrom().item(
                    List.of(Flag.builder("my-flag").setOrigin("my-flag-provider").setInt(counter.incrementAndGet()).build()));
        }

    }

    @Priority(1)
    @Decorator
    public static class CachingDecorator implements FlagProvider {

        @Inject
        @Any
        @Delegate
        FlagProvider delegate;

        @Inject
        @Decorated
        Bean<FlagProvider> decoratedBean;

        @CacheName("quarkus.flags")
        Cache cache;

        @Override
        public Uni<Collection<Flag>> getFlags() {
            String cacheKey = decoratedBean.getQualifiers().stream()
                    .filter(q -> q instanceof Identifier)
                    .map(q -> ((Identifier) q).value())
                    .findFirst()
                    .orElse(decoratedBean.getBeanClass().getName());
            return cache.getAsync(cacheKey, k -> {
                return delegate.getFlags().memoize().indefinitely();
            });
        }

    }
}

package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkiverse.flags.spi.FlagCache;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

public class InMemoryFlagCacheTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(MyFlagProvider.class))
            .overrideRuntimeConfigKey("quarkus.flags.cache.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.flags.cache.ttl", "10m");

    @Inject
    Flags flags;

    @Inject
    FlagCache cache;

    @Inject
    InMemoryFlagProvider inMemory;

    @Identifier("my-flag-provider")
    MyFlagProvider myFlagProvider;

    @BeforeEach
    public void reset() {
        cache.invalidateAll().await().indefinitely();
        myFlagProvider.counter.set(0);
    }

    @Test
    public void testCaching() {
        assertEquals(1, flags.getInt("my-flag"));
        assertEquals(1, flags.getInt("my-flag"));
        assertEquals(1, flags.getInt("my-flag"));
        assertThrows(NoSuchElementException.class, () -> flags.getInt("other-flag"));

        // Invalidate all and verify provider is called again
        cache.invalidateAll().await().indefinitely();
        inMemory.addFlag(Flag.builder("other-flag").setInt(1));

        assertEquals(2, flags.getInt("my-flag"));
        assertEquals(2, flags.getInt("my-flag"));
        assertEquals(1, flags.getInt("other-flag"));

        // Invalidate a single provider
        cache.invalidate("my-flag-provider").await().indefinitely();

        assertEquals(3, flags.getInt("my-flag"));
        assertEquals(3, flags.getInt("my-flag"));
        // other-flag is from the in-memory provider, its cache was not invalidated
        assertEquals(1, flags.getInt("other-flag"));

        // InMemoryFlagProvider.isCacheable() returns false, so changes are visible immediately
        inMemory.removeFlag("other-flag");
        assertThrows(NoSuchElementException.class, () -> flags.getInt("other-flag"));
    }

    @Test
    public void testGetOrComputeFlags() {
        // findAllAndAwait() exercises FlagCache.getOrComputeFlags()
        List<Flag> result = flags.findAllAndAwait();
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getInt());

        // Cached - provider should not be called again
        result = flags.findAllAndAwait();
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getInt());

        cache.invalidateAll().await().indefinitely();

        // After invalidation, the provider is called again
        result = flags.findAllAndAwait();
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getInt());

        // Individual flag lookup should fall back to the cached allFlags
        assertEquals(2, flags.getInt("my-flag"));

        // Verify the counter didn't change (no additional provider call)
        result = flags.findAllAndAwait();
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getInt());

        // Repeated individual lookups should also be served from cache (not re-deriving each time)
        assertEquals(2, flags.getInt("my-flag"));
        assertEquals(2, flags.getInt("my-flag"));
        result = flags.findAllAndAwait();
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getInt());
    }

    @Test
    public void testIsCacheableRespected() {
        // InMemoryFlagProvider.isCacheable() returns false, so its flags are never cached
        inMemory.addFlag(Flag.builder("dynamic-flag").setInt(42));
        assertEquals(42, flags.getInt("dynamic-flag"));

        // Changes are visible immediately without invalidation
        inMemory.removeFlag("dynamic-flag");
        inMemory.addFlag(Flag.builder("dynamic-flag").setInt(99));
        assertEquals(99, flags.getInt("dynamic-flag"));

        inMemory.removeFlag("dynamic-flag");
    }

    @Identifier("my-flag-provider")
    @Singleton
    public static class MyFlagProvider implements FlagProvider {

        final AtomicInteger counter = new AtomicInteger();

        @Override
        public Uni<Collection<Flag>> getFlags() {
            return Uni.createFrom().item(
                    List.of(Flag.builder("my-flag").setOrigin("my-flag-provider").setInt(counter.incrementAndGet()).build()));
        }

        @Override
        public Uni<Flag> getFlag(String feature) {
            if ("my-flag".equals(feature)) {
                return Uni.createFrom()
                        .item(Flag.builder("my-flag").setOrigin("my-flag-provider").setInt(counter.incrementAndGet()).build());
            }
            return Uni.createFrom().nullItem();
        }

    }

}

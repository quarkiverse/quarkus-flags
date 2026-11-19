package io.quarkiverse.flags.cache.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkiverse.flags.cache.CacheFlagCache;
import io.quarkiverse.flags.spi.FlagCache;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

public class CacheFlagCacheTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(MyFlagProvider.class, CacheFlagCache.class))
            .overrideRuntimeConfigKey("quarkus.flags.cache.enabled", "true");

    @Inject
    Flags flags;

    @Inject
    FlagCache cache;

    @Inject
    InMemoryFlagProvider inMemory;

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

    @Identifier("my-flag-provider")
    @Singleton
    public static class MyFlagProvider implements FlagProvider {

        private final AtomicInteger counter = new AtomicInteger();

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

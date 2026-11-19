package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

public class InMemoryFlagCacheProviderDisabledTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(MyFlagProvider.class))
            .overrideRuntimeConfigKey("quarkus.flags.cache.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.flags.cache.my-flag-provider.enabled", "false");

    @Inject
    Flags flags;

    @Identifier("my-flag-provider")
    MyFlagProvider myFlagProvider;

    @BeforeEach
    public void reset() {
        myFlagProvider.counter.set(0);
    }

    @Test
    public void testProviderCacheDisabled() {
        // Each call should invoke the provider because caching is disabled for this provider
        assertEquals(1, flags.getInt("my-flag"));
        assertEquals(2, flags.getInt("my-flag"));
        assertEquals(3, flags.getInt("my-flag"));
    }

    @Test
    public void testProviderCacheDisabledFindAll() {
        // findAll should also bypass the cache
        List<Flag> result = flags.findAllAndAwait();
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getInt());

        result = flags.findAllAndAwait();
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getInt());
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

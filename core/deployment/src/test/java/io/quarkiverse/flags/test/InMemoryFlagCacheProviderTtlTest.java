package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

public class InMemoryFlagCacheProviderTtlTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(MyFlagProvider.class))
            .overrideRuntimeConfigKey("quarkus.flags.cache.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.flags.cache.ttl", "10m")
            .overrideRuntimeConfigKey("quarkus.flags.cache.my-flag-provider.ttl", "1s");

    @Inject
    Flags flags;

    @Test
    public void testProviderTtlOverride() throws InterruptedException {
        assertEquals(1, flags.getInt("my-flag"));
        assertEquals(1, flags.getInt("my-flag"));

        // Wait for the per-provider TTL (1s) to expire
        Thread.sleep(1100);

        // Provider should be called again after per-provider TTL expiry
        assertEquals(2, flags.getInt("my-flag"));
        assertEquals(2, flags.getInt("my-flag"));
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

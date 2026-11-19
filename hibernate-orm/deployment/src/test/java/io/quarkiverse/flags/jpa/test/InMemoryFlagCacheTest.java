package io.quarkiverse.flags.jpa.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.spi.FlagCache;
import io.quarkus.test.QuarkusExtensionTest;

public class InMemoryFlagCacheTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(MyFlag.class))
            .overrideRuntimeConfigKey("quarkus.flags.cache.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.flags.cache.ttl", "10m");

    @Inject
    Flags flags;

    @Inject
    FlagCache cache;

    @Test
    @Transactional
    public void testCaching() {
        assertEquals(0, flags.findAllAndAwait().size());

        MyFlag alpha = new MyFlag();
        alpha.feature = "alpha";
        alpha.value = "true";
        alpha.metadata = Map.of();
        alpha.persistAndFlush();

        // Cache was populated with empty result from the first findAllAndAwait() call
        assertEquals(0, flags.findAllAndAwait().size());

        cache.invalidateAll().await().indefinitely();

        // After invalidation, the provider is called again
        assertEquals(1, flags.findAllAndAwait().size());
        assertTrue(flags.isEnabled("alpha"));

        // Update the flag value
        alpha.value = "false";
        alpha.persistAndFlush();

        // Still cached
        assertTrue(flags.isEnabled("alpha"));

        cache.invalidateAll().await().indefinitely();

        // After invalidation, the updated value is visible
        assertFalse(flags.isEnabled("alpha"));
    }

}

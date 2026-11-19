package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.RegisterFlag;
import io.quarkus.test.QuarkusExtensionTest;

public class NotCacheableFlagProviderTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(MyRegisteredFlags.class))
            .overrideRuntimeConfigKey("quarkus.flags.cache.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.flags.cache.ttl", "10m");

    @Inject
    Flags flags;

    @Test
    public void testRegisteredFlagProviderNotCached() {
        // @RegisterFlag providers are not cacheable, so changes are visible immediately
        assertEquals(10, flags.getInt("registered-flag"));
        MyRegisteredFlags.registeredFlag = 20;
        assertEquals(20, flags.getInt("registered-flag"));
    }

    public static class MyRegisteredFlags {

        @RegisterFlag(name = "registered-flag")
        static volatile int registeredFlag = 10;

    }

}

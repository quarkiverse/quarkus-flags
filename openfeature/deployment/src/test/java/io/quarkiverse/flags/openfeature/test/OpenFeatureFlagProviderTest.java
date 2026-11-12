package io.quarkiverse.flags.openfeature.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import io.quarkiverse.flags.Flag.Value;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.openfeature.OpenFeatureFlags;
import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.vertx.VertxContextSupport;

public class OpenFeatureFlagProviderTest {

    static InMemoryProvider openFeatureProvider = new InMemoryProvider(Map.of(
            "bool-flag", Flag.<Boolean> builder()
                    .variant("on", true)
                    .variant("off", false)
                    .defaultVariant("on")
                    .build(),
            "str-flag", Flag.<String> builder()
                    .variant("hello", "Hello World")
                    .variant("goodbye", "Goodbye")
                    .defaultVariant("hello")
                    .build(),
            "int-flag", Flag.<Integer> builder()
                    .variant("low", 10)
                    .variant("high", 100)
                    .defaultVariant("high")
                    .build(),
            "disabled-flag", Flag.<Boolean> builder()
                    .variant("on", true)
                    .variant("off", false)
                    .defaultVariant("off")
                    .build(),
            "dynamic-flag", Flag.<Boolean> builder()
                    .variant("on", true)
                    .variant("off", false)
                    .defaultVariant("on")
                    .build(),
            "double-flag", Flag.<Double> builder()
                    .variant("pi", 3.14)
                    .variant("e", 2.72)
                    .defaultVariant("pi")
                    .build(),
            "dynamic-str", Flag.<String> builder()
                    .variant("a", "Alpha")
                    .variant("b", "Bravo")
                    .defaultVariant("a")
                    .build()));

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withEmptyApplication()
            .overrideRuntimeConfigKey("quarkus.flags.openfeature.bool-flag.type", "boolean")
            .overrideRuntimeConfigKey("quarkus.flags.openfeature.bool-flag.default-value", "false")
            .overrideRuntimeConfigKey("quarkus.flags.openfeature.str-flag.type", "string")
            .overrideRuntimeConfigKey("quarkus.flags.openfeature.str-flag.default-value", "fallback")
            .overrideRuntimeConfigKey("quarkus.flags.openfeature.int-flag.type", "int")
            .overrideRuntimeConfigKey("quarkus.flags.openfeature.int-flag.default-value", "0")
            .overrideRuntimeConfigKey("quarkus.flags.openfeature.disabled-flag.type", "boolean")
            .overrideRuntimeConfigKey("quarkus.flags.openfeature.disabled-flag.default-value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.openfeature.double-flag.type", "double")
            .overrideRuntimeConfigKey("quarkus.flags.openfeature.double-flag.default-value", "0.0")
            .overrideRuntimeConfigKey("quarkus.flags.openfeature.missing-flag.type", "boolean")
            .overrideRuntimeConfigKey("quarkus.flags.openfeature.missing-flag.default-value", "true");

    @BeforeAll
    static void setup() {
        OpenFeatureAPI.getInstance().setProviderAndWait(openFeatureProvider);
    }

    @AfterAll
    static void teardown() {
        OpenFeatureAPI.getInstance().shutdown();
    }

    @Inject
    Flags flags;

    @Inject
    OpenFeatureFlags openFeatureFlags;

    @Test
    public void testBooleanFlag() {
        assertTrue(flags.isEnabled("bool-flag"));
    }

    @Test
    public void testStringFlag() {
        assertEquals("Hello World", flags.getString("str-flag"));
    }

    @Test
    public void testIntFlag() {
        assertEquals(100, flags.getInt("int-flag"));
    }

    @Test
    public void testDoubleFlag() {
        assertEquals(BigDecimal.valueOf(3.14), flags.getDecimal("double-flag"));
    }

    @Test
    public void testDisabledFlag() {
        assertFalse(flags.isEnabled("disabled-flag"));
    }

    @Test
    public void testMissingFlagReturnsDefault() {
        assertTrue(flags.isEnabled("missing-flag"));
    }

    @Test
    public void testFindAll() {
        var allFlags = flags.findAllAndAwait();
        assertTrue(allFlags.stream().anyMatch(f -> f.feature().equals("bool-flag")));
        assertTrue(allFlags.stream().anyMatch(f -> f.feature().equals("str-flag")));
        assertTrue(allFlags.stream().anyMatch(f -> f.feature().equals("int-flag")));
    }

    @Test
    public void testFlagOrigin() {
        io.quarkiverse.flags.Flag flag = flags.findAndAwait("bool-flag").orElseThrow();
        assertEquals("quarkus.openfeature", flag.origin());
    }

    @Test
    public void testDynamicRegistration() {
        assertFalse(openFeatureFlags.isRegistered("dynamic-flag"));
        assertTrue(flags.findAndAwait("dynamic-flag").isEmpty());

        // Register a boolean flag dynamically
        assertTrue(openFeatureFlags.register("dynamic-flag", false));
        assertTrue(openFeatureFlags.isRegistered("dynamic-flag"));
        assertTrue(flags.isEnabled("dynamic-flag"));

        // Duplicate registration returns false
        assertFalse(openFeatureFlags.register("dynamic-flag", false));

        // Unregister
        assertTrue(openFeatureFlags.unregister("dynamic-flag"));
        assertFalse(openFeatureFlags.isRegistered("dynamic-flag"));
        assertTrue(flags.findAndAwait("dynamic-flag").isEmpty());
    }

    @Test
    public void testDynamicRegistrationWithType() {
        // Register a typed flag dynamically
        assertTrue(openFeatureFlags.register("dynamic-str", "fallback"));
        assertEquals("Alpha", flags.getString("dynamic-str"));

        openFeatureFlags.unregister("dynamic-str");
    }

    @Test
    public void testEvalOffloadedFromEventLoop() throws Throwable {
        // Verify that flag evaluation works when called from a Vert.x event loop
        // thread,
        // i.e. the blocking OpenFeature call is offloaded to a worker thread
        Value value = VertxContextSupport
                .subscribeAndAwait(() -> flags.find("bool-flag")
                        .map(Optional::orElseThrow)
                        .chain(f -> f.compute()));
        assertTrue(value.asBoolean());
    }

    @Test
    public void testConfigFlagsAutoRegistered() {
        assertTrue(openFeatureFlags.isRegistered("bool-flag"));
        assertTrue(openFeatureFlags.isRegistered("str-flag"));
        assertTrue(openFeatureFlags.isRegistered("int-flag"));
    }

}

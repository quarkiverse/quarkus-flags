package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.BooleanValue;
import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.RegisterFlag;
import io.quarkiverse.flags.StringValue;
import io.quarkiverse.flags.TimeSpanFlagEvaluator;
import io.quarkiverse.flags.WithEvaluator;
import io.quarkiverse.flags.WithMetadata;
import io.quarkiverse.flags.spi.FlagEvaluator;
import io.quarkiverse.flags.spi.FlagManager;
import io.quarkiverse.flags.spi.InitializedEvaluatedFlag;
import io.quarkus.test.QuarkusExtensionTest;

public class TimeSpanFlagEvaluatorTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(TimeSpanFlags.class))
            // start-only in the past -> enabled
            .overrideRuntimeConfigKey("quarkus.flags.runtime.alpha.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.alpha.meta.evaluator",
                    TimeSpanFlagEvaluator.ID)
            .overrideRuntimeConfigKey("quarkus.flags.runtime.alpha.meta.start-time",
                    "2011-11-01T10:15:30+01:00[Europe/Prague]")
            // start-only in the future -> disabled
            .overrideRuntimeConfigKey("quarkus.flags.runtime.bravo.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.bravo.meta.evaluator",
                    TimeSpanFlagEvaluator.ID)
            .overrideRuntimeConfigKey("quarkus.flags.runtime.bravo.meta.start-time",
                    "2115-11-01T10:15:30+01:00[Europe/Prague]")
            // both bounds, now is within -> enabled
            .overrideRuntimeConfigKey("quarkus.flags.runtime.charlie.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.charlie.meta.evaluator",
                    TimeSpanFlagEvaluator.ID)
            .overrideRuntimeConfigKey("quarkus.flags.runtime.charlie.meta.start-time",
                    "2001-01-01T10:15:30+01:00[Europe/Prague]")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.charlie.meta.end-time",
                    "2115-11-01T10:15:30+01:00[Europe/Prague]")
            // no bounds -> enabled
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.meta.evaluator",
                    TimeSpanFlagEvaluator.ID)
            // end-only in the past -> disabled
            .overrideRuntimeConfigKey("quarkus.flags.runtime.echo.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.echo.meta.evaluator", TimeSpanFlagEvaluator.ID)
            .overrideRuntimeConfigKey("quarkus.flags.runtime.echo.meta.end-time",
                    "2011-11-01T10:15:30+01:00[Europe/Prague]");

    @Inject
    Flags flags;

    @Inject
    FlagManager flagManager;

    @Test
    public void testFlag() {
        assertTrue(flags.isEnabled("alpha"));
        assertEquals("true", flags.findAndAwait("alpha").orElseThrow().getString());
        assertFalse(flags.isEnabled("bravo"));
        assertEquals(0, flags.findAndAwait("bravo").orElseThrow().getInt());
        assertTrue(flags.isEnabled("charlie"));
        assertEquals(1, flags.getInt("charlie"));
        assertTrue(flags.isEnabled("delta"));
        assertFalse(flags.isEnabled("echo"));
    }

    @Test
    public void testInvalidFormat() {
        FlagEvaluator evaluator = flagManager.getEvaluator(TimeSpanFlagEvaluator.ID).orElseThrow();
        Flag flag = new InitializedEvaluatedFlag("test", "test",
                Map.of("evaluator", TimeSpanFlagEvaluator.ID, TimeSpanFlagEvaluator.START_TIME,
                        "not-a-date"),
                BooleanValue.TRUE, evaluator);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> flag.computeAndAwait());
        assertTrue(e.getMessage().contains("Invalid start-time"));
        assertTrue(e.getMessage().contains("not-a-date"));
    }

    @Test
    public void testNonBooleanInitialValue() {
        FlagEvaluator evaluator = flagManager.getEvaluator(TimeSpanFlagEvaluator.ID).orElseThrow();
        Flag flag = new InitializedEvaluatedFlag("test", "test",
                Map.of("evaluator", TimeSpanFlagEvaluator.ID), new StringValue("hello"), evaluator);
        assertEquals("hello", flag.computeAndAwait().asString());
    }

    @Test
    public void testStartAfterEnd() {
        FlagEvaluator evaluator = flagManager.getEvaluator(TimeSpanFlagEvaluator.ID).orElseThrow();
        Flag flag = new InitializedEvaluatedFlag("test", "test",
                Map.of("evaluator", TimeSpanFlagEvaluator.ID,
                        TimeSpanFlagEvaluator.START_TIME,
                        "2115-11-01T10:15:30+01:00[Europe/Prague]",
                        TimeSpanFlagEvaluator.END_TIME,
                        "2011-11-01T10:15:30+01:00[Europe/Prague]"),
                BooleanValue.TRUE, evaluator);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> flag.computeAndAwait());
        assertTrue(e.getMessage().contains("must be before"));
    }

    @Test
    public void testRegisteredFlag() {
        // start-time in the past -> enabled
        assertTrue(Flag.get("foxtrot").isEnabled());
        // start-time in the future -> disabled
        assertFalse(Flag.get("golf").isEnabled());
    }

    public static class TimeSpanFlags {

        @RegisterFlag
        @WithEvaluator(TimeSpanFlagEvaluator.ID)
        @WithMetadata(key = TimeSpanFlagEvaluator.START_TIME, value = "2011-11-01T10:15:30+01:00[Europe/Prague]")
        static final boolean foxtrot = true;

        @RegisterFlag
        @WithEvaluator(TimeSpanFlagEvaluator.ID)
        @WithMetadata(key = TimeSpanFlagEvaluator.START_TIME, value = "2115-11-01T10:15:30+01:00[Europe/Prague]")
        static final boolean golf = true;
    }

}

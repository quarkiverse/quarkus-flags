package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.BooleanValue;
import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flag.ComputationContext;
import io.quarkiverse.flags.Flag.Value;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.RegisterFlag;
import io.quarkiverse.flags.WithEvaluator;
import io.quarkiverse.flags.WithMetadata;
import io.quarkiverse.flags.spi.FlagEvaluator;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

public class RegisterFlagTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(
                    root -> root.addClasses(MyFlags.class, FlagConsumer.class, NegateEvaluator.class));

    @Inject
    Flags flags;

    @Inject
    FlagConsumer consumer;

    @Test
    public void testRegisteredFlags() {
        assertEquals(10, flags.findAllAndAwait().stream()
                .filter(f -> f.origin().contains("MyFlags")).count());

        // Boolean field
        assertTrue(Flag.get("alpha").isEnabled());
        // Int field
        assertEquals(42, Flag.get("bravo").getInt());
        // String field
        assertEquals("hello", Flag.get("charlie").getString());
        // BigDecimal field
        assertEquals(new BigDecimal("3.14"), Flag.get("delta").getDecimal());
        // Boolean method (no params)
        assertFalse(Flag.get("echo").isEnabled());
        // Int method with ComputationContext
        assertEquals(100, Flag.get("foxtrot").getInt());
        // Flag.Value field
        assertTrue(Flag.get("golf").computeAndAwait().asBoolean());

        // @WithMetadata
        assertEquals("bar", Flag.get("hotel").metadata().get("foo"));

        // Evaluator - negateEval negates the boolean value
        assertFalse(Flag.get("india").isEnabled());
    }

    @Test
    public void testDynamicValues() {
        // Field: change value and verify the flag reflects it
        assertEquals(42, Flag.get("bravo").getInt());
        MyFlags.bravo = 99;
        try {
            assertEquals(99, Flag.get("bravo").getInt());
        } finally {
            MyFlags.bravo = 42;
        }

        assertEquals("hello", Flag.get("charlie").getString());
        MyFlags.charlie = "world";
        try {
            assertEquals("world", Flag.get("charlie").getString());
        } finally {
            MyFlags.charlie = "hello";
        }

        // Method: change backing field and verify the flag reflects it
        assertEquals(10, Flag.get("juliet").getInt());
        MyFlags.julietValue = 20;
        try {
            assertEquals(20, Flag.get("juliet").getInt());
        } finally {
            MyFlags.julietValue = 10;
        }

    }

    @Test
    public void testBytecodeTransformation() {
        // Consumer reads go through Flag.get() due to bytecode transformation
        assertTrue(consumer.getAlpha());
        assertEquals(42, consumer.getBravo());
        assertEquals("hello", consumer.getCharlie());
        assertEquals(new BigDecimal("3.14"), consumer.getDelta());
        assertFalse(consumer.getEcho());
        assertEquals(100, consumer.getFoxtrot());
    }

    public static class MyFlags {

        @RegisterFlag
        static final boolean alpha = true;

        @RegisterFlag
        static volatile int bravo = 42;

        @RegisterFlag
        static volatile String charlie = "hello";

        @RegisterFlag
        static volatile BigDecimal delta = new BigDecimal("3.14");

        @RegisterFlag
        static boolean echo() {
            return false;
        }

        @RegisterFlag
        static int foxtrot(ComputationContext ctx) {
            Objects.requireNonNull(ctx);
            return 100;
        }

        @RegisterFlag
        static volatile Flag.Value golf = BooleanValue.TRUE;

        @RegisterFlag
        @WithMetadata(key = "foo", value = "bar")
        static volatile boolean hotel = true;

        @RegisterFlag
        @WithEvaluator("negateEval")
        static volatile boolean india = true;

        static volatile int julietValue = 10;

        @RegisterFlag
        static int juliet() {
            return julietValue;
        }

    }

    @Singleton
    public static class FlagConsumer {

        public boolean getAlpha() {
            return MyFlags.alpha;
        }

        public int getBravo() {
            return MyFlags.bravo;
        }

        public String getCharlie() {
            return MyFlags.charlie;
        }

        public BigDecimal getDelta() {
            return MyFlags.delta;
        }

        public boolean getEcho() {
            return MyFlags.echo();
        }

        public int getFoxtrot() {
            return MyFlags.foxtrot(ComputationContext.EMPTY);
        }
    }

    @Identifier("negateEval")
    @Singleton
    public static class NegateEvaluator implements FlagEvaluator {

        @Override
        public Uni<Value> evaluate(Flag flag, Value initialValue, ComputationContext computationContext) {
            return BooleanValue.createUni(!initialValue.asBoolean());
        }
    }
}

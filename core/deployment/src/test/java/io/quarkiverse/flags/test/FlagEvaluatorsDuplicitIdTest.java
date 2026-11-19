package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flag.ComputationContext;
import io.quarkiverse.flags.Flag.Value;
import io.quarkiverse.flags.spi.FlagEvaluator;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

public class FlagEvaluatorsDuplicitIdTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(FooEvaluator.class, BarEvaluator.class))
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void testFailure() {
        fail();
    }

    @Identifier("foo")
    @Singleton
    public static class FooEvaluator implements FlagEvaluator {

        @Override
        public Uni<Value> evaluate(Flag flag, Value initialValue, ComputationContext computationContext) {
            throw new IllegalArgumentException();
        }

    }

    @Identifier("foo")
    @Singleton
    public static class BarEvaluator implements FlagEvaluator {

        @Override
        public Uni<Value> evaluate(Flag flag, Value initialValue, ComputationContext computationContext) {
            throw new IllegalArgumentException();
        }

    }

}

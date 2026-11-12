package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.BooleanValue;
import io.quarkiverse.flags.CompositeFlagEvaluator;
import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flag.ComputationContext;
import io.quarkiverse.flags.Flag.Value;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.spi.FlagEvaluator;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

public class CompositeFlagEvaluatorTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(FooEvaluator.class, BarEvaluator.class))
            .overrideRuntimeConfigKey("quarkus.flags.runtime.alpha.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.alpha.meta.evaluator", CompositeFlagEvaluator.ID)
            .overrideRuntimeConfigKey("quarkus.flags.runtime.alpha.meta.sub-evaluators", "foo, bar")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.alpha.meta.baz", "1");

    static final List<String> EVALS = new CopyOnWriteArrayList<>();

    @Inject
    Flags flags;

    @Test
    public void testFlag() {
        assertTrue(flags.isEnabled("alpha"));
        assertEquals(2, EVALS.size());
        assertEquals("foo", EVALS.get(0));
    }

    @Identifier("foo")
    @Singleton
    public static class FooEvaluator implements FlagEvaluator {

        @Override
        public Uni<Value> evaluate(Flag flag, Value initialValue, ComputationContext computationContext) {
            EVALS.add("foo");
            if ("1".equals(flag.metadata().get("baz"))) {
                return BooleanValue.createUni(!initialValue.asBoolean());
            }
            throw new IllegalStateException();
        }

    }

    @Identifier("bar")
    @Singleton
    public static class BarEvaluator implements FlagEvaluator {

        @Override
        public Uni<Value> evaluate(Flag flag, Value initialValue, ComputationContext computationContext) {
            EVALS.add("bar");
            return BooleanValue.createUni(!initialValue.asBoolean());
        }

    }

}

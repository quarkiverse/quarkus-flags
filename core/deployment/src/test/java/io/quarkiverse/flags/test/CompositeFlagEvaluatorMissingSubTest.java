package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.CompositeFlagEvaluator;
import io.quarkiverse.flags.Flags;
import io.quarkus.test.QuarkusExtensionTest;

public class CompositeFlagEvaluatorMissingSubTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .overrideRuntimeConfigKey("quarkus.flags.runtime.alpha.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.alpha.meta.evaluator", CompositeFlagEvaluator.ID)
            .overrideRuntimeConfigKey("quarkus.flags.runtime.alpha.meta.sub-evaluators", "nonexistent");

    @Inject
    Flags flags;

    @Test
    public void testMissingSubEvaluator() {
        assertThrows(IllegalStateException.class, () -> flags.isEnabled("alpha"));
    }
}

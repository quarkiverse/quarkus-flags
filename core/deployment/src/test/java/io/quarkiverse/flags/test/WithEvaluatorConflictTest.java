package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.RegisterFlag;
import io.quarkiverse.flags.WithEvaluator;
import io.quarkiverse.flags.WithMetadata;
import io.quarkiverse.flags.spi.FlagEvaluator;
import io.quarkus.test.QuarkusExtensionTest;

public class WithEvaluatorConflictTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClass(ConflictFlags.class))
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void testFailure() {
        fail();
    }

    public static class ConflictFlags {

        @RegisterFlag
        @WithEvaluator("someEval")
        @WithMetadata(key = FlagEvaluator.META_KEY, value = "anotherEval")
        static volatile boolean alpha = true;
    }
}

package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.RegisterFlag;
import io.quarkus.test.QuarkusExtensionTest;

public class RegisterFlagNonStaticFieldTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClass(NonStaticFlags.class))
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void testFailure() {
        fail();
    }

    public static class NonStaticFlags {

        @RegisterFlag
        boolean alpha = true;
    }
}

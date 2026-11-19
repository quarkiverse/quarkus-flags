package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.RegisterFlag;
import io.quarkus.test.QuarkusExtensionTest;

public class RegisterFlagWrongParamTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClass(WrongParamFlags.class))
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void testFailure() {
        fail();
    }

    public static class WrongParamFlags {

        @RegisterFlag
        static boolean alpha(String name) {
            return true;
        }
    }
}

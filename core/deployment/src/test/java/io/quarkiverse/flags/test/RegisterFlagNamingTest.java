package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.RegisterFlag;
import io.quarkus.test.QuarkusExtensionTest;

public class RegisterFlagNamingTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClass(NamingFlags.class));

    @Inject
    Flags flags;

    @Test
    public void testDefaultElementName() {
        assertTrue(Flag.get("alpha").isEnabled());
    }

    @Test
    public void testFqcnElementName() {
        String fqcnName = NamingFlags.class.getName() + ".bravo";
        assertTrue(Flag.get(fqcnName).isEnabled());
    }

    @Test
    public void testExplicitName() {
        assertTrue(Flag.get("my-custom-flag").isEnabled());
    }

    @Test
    public void testDefaultMethodName() {
        assertFalse(Flag.get("delta").isEnabled());
    }

    public static class NamingFlags {

        @RegisterFlag
        static volatile boolean alpha = true;

        @RegisterFlag(name = RegisterFlag.FQCN_ELEMENT_NAME)
        static volatile boolean bravo = true;

        @RegisterFlag(name = "my-custom-flag")
        static volatile boolean charlie = true;

        @RegisterFlag
        static boolean delta() {
            return false;
        }
    }
}

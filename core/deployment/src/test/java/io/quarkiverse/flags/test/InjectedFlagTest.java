package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.NoSuchElementException;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkus.test.QuarkusExtensionTest;

public class InjectedFlagTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withEmptyApplication();

    @Inject
    Flags flags;

    @Inject
    InMemoryFlagProvider inMemoryFlagProvider;

    @Test
    public void testFlags() {
        assertThrows(NoSuchElementException.class, () -> Flag.get("alpha"));
        inMemoryFlagProvider.addFlag(Flag.builder("alpha").setEnabled(true));
        Flag alpha = Flag.get("alpha");
        assertNotNull(alpha);
        assertTrue(alpha.isEnabled());
        assertNull(alpha.metadata().get("foo"));
        inMemoryFlagProvider.removeFlag("alpha");
        assertThrows(NoSuchElementException.class, () -> Flag.get("alpha"));
        inMemoryFlagProvider.addFlag(Flag.builder("alpha").setEnabled(false).setMetadata(Map.of("foo", "bar")));
        alpha = Flag.get("alpha");
        assertFalse(alpha.isEnabled());
        assertEquals("bar", alpha.metadata().get("foo"));
    }

    @Test
    public void testStaticGet() {
        assertThrows(IllegalArgumentException.class, () -> Flag.get(null));
        assertThrows(IllegalArgumentException.class, () -> Flag.get(""));
        assertThrows(NoSuchElementException.class, () -> Flag.get("charlie"));
        inMemoryFlagProvider.addFlag(Flag.builder("charlie").setEnabled(true));
        Flag charlie = Flag.get("charlie");
        assertNotNull(charlie);
        assertEquals("charlie", charlie.feature());
        assertTrue(charlie.isEnabled());
    }

    @Test
    public void testFindValidation() {
        assertThrows(IllegalArgumentException.class, () -> flags.findAndAwait(null));
        assertThrows(IllegalArgumentException.class, () -> flags.findAndAwait(""));
    }

}

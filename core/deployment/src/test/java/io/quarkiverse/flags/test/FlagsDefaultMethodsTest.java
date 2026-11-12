package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkus.test.QuarkusExtensionTest;

public class FlagsDefaultMethodsTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest();

    @Inject
    InMemoryFlagProvider inMemoryFlagProvider;

    @Inject
    Flags flags;

    @Test
    public void testDecimalFlag() {
        inMemoryFlagProvider.addFlag(Flag.builder("price").setDecimal(new BigDecimal("19.99")));
        assertEquals(new BigDecimal("19.99"), flags.getDecimal("price"));
        assertEquals(new BigDecimal("19.99"), flags.getDecimal("price", BigDecimal.ZERO));

        // non-existent flag returns default
        assertEquals(BigDecimal.TEN, flags.getDecimal("nonexistent", BigDecimal.TEN));

        // non-existent flag without default throws
        assertThrows(NoSuchElementException.class, () -> flags.getDecimal("nonexistent"));
    }

    @Test
    public void testIntFlag() {
        inMemoryFlagProvider.addFlag(Flag.builder("count").setInt(42));
        assertEquals(42, flags.getInt("count"));
        assertEquals(42, flags.getInt("count", 0));

        // non-existent flag returns default
        assertEquals(99, flags.getInt("nonexistent_int", 99));

        // non-existent flag without default throws
        assertThrows(NoSuchElementException.class, () -> flags.getInt("nonexistent_int"));
    }

    @Test
    public void testStringFlag() {
        inMemoryFlagProvider.addFlag(Flag.builder("label").setString("hello"));
        assertEquals("hello", flags.getString("label"));
        assertEquals("hello", flags.getString("label", "fallback"));

        // non-existent flag returns default
        assertEquals("fallback", flags.getString("nonexistent_str", "fallback"));

        // non-existent flag without default throws
        assertThrows(NoSuchElementException.class, () -> flags.getString("nonexistent_str"));
    }

    @Test
    public void testBooleanFlagDefaults() {
        inMemoryFlagProvider.addFlag(Flag.builder("enabled"));
        assertTrue(flags.isEnabled("enabled"));
        assertTrue(flags.isEnabled("enabled", false));

        inMemoryFlagProvider.addFlag(Flag.builder("disabled").setEnabled(false));
        assertFalse(flags.isEnabled("disabled"));
        assertFalse(flags.isEnabled("disabled", true));

        // non-existent flag returns default
        assertTrue(flags.isEnabled("nonexistent_bool", true));
        assertFalse(flags.isEnabled("nonexistent_bool", false));

        // non-existent flag without default throws
        assertThrows(NoSuchElementException.class, () -> flags.isEnabled("nonexistent_bool"));
    }

    @Test
    public void testConversionDefaultsOnExistingFlag() {
        inMemoryFlagProvider.addFlag(Flag.builder("text").setString("not_a_number"));

        // conversion fails for non-numeric string, default returned
        assertEquals(BigDecimal.TEN, flags.getDecimal("text", BigDecimal.TEN));
        assertEquals(99, flags.getInt("text", 99));
        assertTrue(flags.isEnabled("text", true));

        // Flag-level getDecimal/getInt
        Flag textFlag = flags.findAndAwait("text").orElseThrow();
        assertEquals(BigDecimal.TEN, textFlag.getDecimal(BigDecimal.TEN));
        assertEquals(42, textFlag.getInt(42));
        assertThrows(NoSuchElementException.class, textFlag::getDecimal);
        assertThrows(NoSuchElementException.class, textFlag::getInt);
    }

    @Test
    public void testFindAll() {
        inMemoryFlagProvider.addFlag(Flag.builder("a"));
        inMemoryFlagProvider.addFlag(Flag.builder("b").setInt(1));
        assertTrue(flags.findAllAndAwait().size() >= 2);
    }
}

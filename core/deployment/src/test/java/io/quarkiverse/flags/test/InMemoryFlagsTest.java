package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.BooleanValue;
import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkiverse.flags.InMemoryFlagProvider.FlagAdded;
import io.quarkiverse.flags.InMemoryFlagProvider.FlagRemoved;
import io.quarkiverse.flags.StringValue;
import io.quarkus.test.QuarkusExtensionTest;

public class InMemoryFlagsTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(FlagObservers.class));

    @Inject
    InMemoryFlagProvider inMemoryFlagProvider;

    @Inject
    Flags flags;

    @Inject
    FlagObservers flagObservers;

    @Test
    public void testFlags() {
        assertEquals(0, flags.findAllAndAwait().size());
        assertEquals(0, flagObservers.added.size());
        assertEquals(0, flagObservers.removed.size());
        AtomicBoolean charlieValue = new AtomicBoolean(true);

        inMemoryFlagProvider.addFlag(Flag.builder("alpha"));
        inMemoryFlagProvider.addFlag(Flag.builder("bravo")
                .setEnabled(false));
        inMemoryFlagProvider.addFlag(Flag.builder("charlie")
                .setCompute(cc -> BooleanValue.from(charlieValue.get())));
        inMemoryFlagProvider.addFlag(Flag.builder("delta")
                .setComputeAsync(cc -> StringValue.createUni("no")));
        assertEquals(4, flagObservers.added.size());
        assertEquals(0, flagObservers.removed.size());

        Flag.Value alphaValue = flags.findAndAwait("alpha").orElseThrow().computeAndAwait();
        assertTrue(alphaValue.asBoolean());
        assertEquals("true", alphaValue.asString());
        assertEquals(1, alphaValue.asInt());

        assertFalse(flags.findAndAwait("bravo").orElseThrow().computeAndAwait().asBoolean());
        assertTrue(flags.isEnabled("charlie"));
        charlieValue.set(false);
        assertFalse(flags.isEnabled("charlie"));

        Flag.Value deltaValue = flags.findAndAwait("delta").orElseThrow().computeAndAwait();
        assertThrows(NoSuchElementException.class, () -> deltaValue.asBoolean());
        assertEquals("no", deltaValue.asString());
        assertThrows(NoSuchElementException.class, () -> deltaValue.asInt());

        // Flag-level defaults: conversion fails, default returned
        assertTrue(flags.findAndAwait("delta").orElseThrow().isEnabled(true));
        assertEquals(42, flags.findAndAwait("delta").orElseThrow().getInt(42));
        // Flag-level defaults: conversion succeeds, actual value returned
        assertEquals("no", flags.findAndAwait("delta").orElseThrow().getString("fallback"));

        // Flags-level defaults: non-existent flag, default returned
        assertFalse(flags.isEnabled("nonexistent", false));
        assertEquals("fallback", flags.getString("nonexistent", "fallback"));
        assertEquals(99, flags.getInt("nonexistent", 99));

        flags.findAllAndAwait().forEach(f -> inMemoryFlagProvider.removeFlag(f.feature()));
        assertEquals(0, flags.findAllAndAwait().size());
        assertEquals(4, flagObservers.added.size());
        assertEquals(4, flagObservers.removed.size());
    }

    @Singleton
    public static class FlagObservers {

        final List<Flag> added = new CopyOnWriteArrayList<>();
        final List<Flag> removed = new CopyOnWriteArrayList<>();

        void flagAdded(@Observes FlagAdded flagAdded) {
            added.add(flagAdded.flag());
        }

        void flagRemoved(@Observes FlagRemoved flagRemoved) {
            removed.add(flagRemoved.flag());
        }

    }

}

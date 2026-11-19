package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkiverse.flags.spi.ComponentOrder;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

public class FlagProviderProducerTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(Producers.class));

    @Inject
    InMemoryFlagProvider inMemoryFlagProvider;

    @Inject
    Flags flags;

    @Test
    public void testProducerProviderHasPriority() {
        // The producer provider declares @ComponentOrder(before = "quarkus.in-memory")
        // so its flags should take precedence over in-memory flags
        inMemoryFlagProvider.addFlag(Flag.builder("alpha").setEnabled(false));
        // "alpha" is also provided by the producer provider with value true
        assertTrue(flags.isEnabled("alpha"));
    }

    @Test
    public void testProducerProviderFlags() {
        assertEquals("hello", flags.getString("bravo"));
    }

    @ApplicationScoped
    public static class Producers {

        @Produces
        @Singleton
        @Identifier("producer")
        @ComponentOrder(before = InMemoryFlagProvider.ID)
        FlagProvider createProvider() {
            return new FlagProvider() {
                @Override
                public Uni<Collection<Flag>> getFlags() {
                    return Uni.createFrom().item(List.of(
                            Flag.builder("alpha").setOrigin("producer").setEnabled(true).build(),
                            Flag.builder("bravo").setOrigin("producer").setString("hello").build()));
                }
            };
        }

    }

}

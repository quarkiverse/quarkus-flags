package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.stream.StreamSupport;

import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkiverse.flags.spi.ImmutableBooleanValue;
import io.quarkiverse.flags.spi.ImmutableFlag;
import io.quarkus.test.QuarkusUnitTest;

public class FlagProviderDecoratorTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(FlagProviderDecorator.class));

    @Inject
    InMemoryFlagProvider inMemoryFlagProvider;

    @Inject
    Flags flags;

    @Test
    public void testFlags() {
        inMemoryFlagProvider.newFlag("alpha")
                .setEnabled(true)
                .register();
        assertFalse(flags.find("alpha").orElseThrow().isOn());
    }

    @Priority(5)
    @Decorator
    public static class FlagProviderDecorator implements FlagProvider {

        @Inject
        @Delegate
        FlagProvider delegate;

        @Override
        public Iterable<Flag> getFlags() {
            return StreamSupport.stream(delegate.getFlags().spliterator(), false).<Flag> map(f -> {
                return new ImmutableFlag(f.feature(), f.metadata(), ImmutableBooleanValue.FALSE);
            }).toList();
        }

        @Override
        public int getPriority() {
            return delegate.getPriority();
        }

    }
}

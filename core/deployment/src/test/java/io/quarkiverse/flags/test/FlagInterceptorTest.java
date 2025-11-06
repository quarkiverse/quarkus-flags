package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flag.ComputationContext;
import io.quarkiverse.flags.Flag.State;
import io.quarkiverse.flags.FlagManager;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkiverse.flags.spi.FlagInterceptor;
import io.quarkiverse.flags.spi.ImmutableBooleanState;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

public class FlagInterceptorTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(FlagInterceptor1.class, FlagInterceptor2.class));

    @Inject
    InMemoryFlagProvider inMemoryFlagProvider;

    @Inject
    FlagManager manager;

    @Test
    public void testFlags() {
        inMemoryFlagProvider.newFlag("alpha")
                .setEnabled(true)
                .add();
        assertFalse(manager.getFlag("alpha").orElseThrow().computeAndAwait().getBoolean());
    }

    @Priority(10)
    @Singleton
    public static class FlagInterceptor1 implements FlagInterceptor {

        @Override
        public Uni<State> afterCompute(Flag flag, State state, ComputationContext computationContext) {
            if (!state.getBoolean()) {
                throw new IllegalStateException();
            }
            return Uni.createFrom().item(state);
        }

    }

    @Priority(5)
    @Singleton
    public static class FlagInterceptor2 implements FlagInterceptor {

        @Override
        public Uni<State> afterCompute(Flag flag, State state, ComputationContext computationContext) {
            // just invert the state
            return Uni.createFrom().item(ImmutableBooleanState.from(!state.getBoolean()));
        }

    }
}

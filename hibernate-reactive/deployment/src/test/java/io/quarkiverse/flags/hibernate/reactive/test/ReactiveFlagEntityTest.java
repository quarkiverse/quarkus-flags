package io.quarkiverse.flags.hibernate.reactive.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.BooleanValue;
import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flag.ComputationContext;
import io.quarkiverse.flags.Flag.Value;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.spi.FlagEvaluator;
import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

public class ReactiveFlagEntityTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(MyReactiveFlag.class, InvertingFlagEvaluator.class))
    //.overrideConfigKey("quarkus.hibernate-orm.log.sql", "true")
    ;

    @Inject
    Mutiny.SessionFactory sf;

    @Inject
    Flags flags;

    @Test
    public void testFlagSource() throws Throwable {
        assertEquals(0, flags.findAllAndAwait().size());

        MyReactiveFlag alpha = new MyReactiveFlag();
        alpha.feature = "alpha";
        alpha.value = "false";
        alpha.metadata = Map.of("foo", "bar", FlagEvaluator.META_KEY, "inverting");
        VertxContextSupport.subscribeAndAwait(() -> sf.withTransaction(s -> s.persist(alpha)));

        Flag alphaFlag = flags.findAndAwait("alpha").orElseThrow();
        assertEquals("bar", alphaFlag.metadata().get("foo"));
        assertEquals("inverting", alphaFlag.metadata().get(FlagEvaluator.META_KEY));
        Flag.Value alphaState = alphaFlag.computeAndAwait();
        assertTrue(alphaState.asBoolean());
        assertEquals("true", alphaState.asString());
    }

    @Identifier("inverting")
    @Singleton
    public static class InvertingFlagEvaluator implements FlagEvaluator {

        @Override
        public Uni<Value> evaluate(Flag flag, Value initialValue, ComputationContext computationContext) {
            return BooleanValue.createUni(!initialValue.asBoolean());
        }

    }

}

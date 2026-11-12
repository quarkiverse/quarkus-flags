package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.BooleanValue;
import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flag.ComputationContext;
import io.quarkiverse.flags.Flag.Value;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.spi.FlagEvaluator;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

public class ConfigFlagsTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> {
                root.addClass(DeltaEvaluator.class)
                        .addAsResource(new StringAsset("""
                                quarkus.flags.build.alpha.value=true
                                quarkus.flags.build.bravo.value=0
                                """), "application.properties");
            })
            .overrideRuntimeConfigKey("quarkus.flags.runtime.charlie.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.meta.evaluator", "deltaEval")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.meta.usernames", "foo,bar,baz");

    @Inject
    Flags flags;

    @Test
    public void testFlags() {
        List<Flag> all = flags.findAllAndAwait();
        assertEquals(4, all.size(), all.toString());
        assertTrue(flags.findAndAwait("alpha").orElseThrow().isEnabled());
        assertFalse(flags.findAndAwait("bravo").orElseThrow().computeAndAwait().asBoolean());
        assertEquals(0, flags.findAndAwait("bravo").orElseThrow().getInt());
        assertTrue(flags.isEnabled("charlie"));
        assertFalse(flags.isEnabled("delta"));
        assertTrue(Flag.get("alpha").isEnabled());

        assertFalse(Flag.get("bravo").computeAndAwait().asBoolean());
        assertEquals("0", Flag.get("bravo").getString());

        Flag delta = Flag.get("delta");
        assertEquals("deltaEval", delta.metadata().get(FlagEvaluator.META_KEY));
        assertTrue(delta.computeAndAwait(Flag.ComputationContext.of("username", "foo")).asBoolean());
        assertFalse(delta.computeAndAwait(Flag.ComputationContext.of("username", "qux")).asBoolean());
    }

    @Identifier("deltaEval")
    @Singleton
    public static class DeltaEvaluator implements FlagEvaluator {

        @Override
        public Uni<Value> evaluate(Flag flag, Value initialValue, ComputationContext computationContext) {
            if (!initialValue.asBoolean()) {
                throw new IllegalStateException();
            }
            String username = (String) computationContext.get("username");
            if (username == null) {
                return BooleanValue.createUni(!initialValue.asBoolean());
            }
            String[] usernames = flag.metadata().get("usernames").split(",");
            String match = Arrays.stream(usernames).filter(u -> username.equals(u)).findFirst().orElse(null);
            return Uni.createFrom().item(BooleanValue.from(match != null));
        }

    }

}

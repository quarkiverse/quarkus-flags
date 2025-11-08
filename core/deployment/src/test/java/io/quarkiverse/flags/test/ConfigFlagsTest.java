package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Feature;
import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flag.ComputationContext;
import io.quarkiverse.flags.Flag.Value;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.spi.FlagEvaluator;
import io.quarkiverse.flags.spi.ImmutableBooleanValue;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

public class ConfigFlagsTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClass(DeltaEvaluator.class)
                        .addAsResource(new StringAsset("""
                                quarkus.flags.build.alpha.value=true
                                quarkus.flags.build.bravo.value=0
                                """), "application.properties");
            })
            .overrideRuntimeConfigKey("quarkus.flags.runtime.charlie.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.meta.evaluator", "deltaEval")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.meta.usernames", "foo,bar,baz");

    @Inject
    Flags flags;

    @Feature("alpha")
    Flag alpha;

    @Feature("foo")
    Optional<Flag> foo;

    @Feature("delta")
    Optional<Flag> delta;

    @Feature("bravo")
    Instance<Flag> bravo;

    @Test
    public void testFlags() {
        List<Flag> all = flags.findAll();
        assertEquals(4, all.size(), all.toString());
        assertTrue(flags.find("alpha").orElseThrow().isOn());
        assertFalse(flags.find("bravo").orElseThrow().computeAndAwait().asBoolean());
        assertEquals(0, flags.find("bravo").orElseThrow().getInt());
        assertTrue(flags.find("charlie").orElseThrow().computeAndAwait().asBoolean());
        assertFalse(flags.find("delta").orElseThrow().computeAndAwait().asBoolean());
        assertTrue(alpha.isOn());
        assertTrue(foo.isEmpty());

        assertFalse(bravo.get().computeAndAwait().asBoolean());
        assertEquals("0", bravo.get().getString());

        Flag deltaFlag = delta.orElseThrow();
        assertEquals("deltaEval", deltaFlag.metadata().get(FlagEvaluator.METADATA_KEY));
        assertTrue(deltaFlag.computeAndAwait(Flag.ComputationContext.of("username", "foo")).asBoolean());
        assertFalse(deltaFlag.computeAndAwait(Flag.ComputationContext.of("username", "qux")).asBoolean());
    }

    @Singleton
    public static class DeltaEvaluator implements FlagEvaluator {

        @Override
        public String id() {
            return "deltaEval";
        }

        @Override
        public Uni<Value> evaluate(Flag flag, Value initialValue, ComputationContext computationContext) {
            if (!initialValue.asBoolean()) {
                throw new IllegalStateException();
            }
            String username = computationContext.get("username");
            if (username == null) {
                return Uni.createFrom().item(ImmutableBooleanValue.from(!initialValue.asBoolean()));
            }
            String[] usernames = flag.metadata().get("usernames").split(",");
            String match = Arrays.stream(usernames).filter(u -> username.equals(u)).findFirst().orElse(null);
            return Uni.createFrom().item(ImmutableBooleanValue.from(match != null));
        }

    }

}

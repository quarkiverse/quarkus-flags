package io.quarkiverse.flags.jpa.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.TestTransaction;

public class FlagDefinitionTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClass(MyFlag.class));

    @Inject
    Flags flags;

    @TestTransaction
    @Test
    public void testFlagDefinition() {
        assertEquals(0, flags.findAll().size());

        MyFlag alpha = new MyFlag();
        alpha.feature = "alpha";
        alpha.value = "true";
        alpha.metadata = Map.of("foo", "bar");
        alpha.persist();

        Flag alphaFlag = flags.find("alpha").orElseThrow();
        assertEquals("bar", alphaFlag.metadata().get("foo"));
        Flag.Value alphaState = alphaFlag.computeAndAwait();
        assertTrue(alphaState.asBoolean());
        assertEquals("true", alphaState.asString());
    }

}

package io.quarkiverse.flags.jpa.test;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

public class FlagEntityFeatureNotSetTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(MyFlagNoFeature.class))
            .setExpectedException(IllegalArgumentException.class);

    @Test
    public void testFailure() {
        fail();
    }

}

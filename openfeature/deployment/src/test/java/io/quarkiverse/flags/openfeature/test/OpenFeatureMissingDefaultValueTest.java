package io.quarkiverse.flags.openfeature.test;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.config.ConfigValidationException;

public class OpenFeatureMissingDefaultValueTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withEmptyApplication()
            .overrideRuntimeConfigKey("quarkus.flags.openfeature.my-flag.type", "boolean")
            .setExpectedException(ConfigValidationException.class);

    @Test
    public void testFailure() {
        fail();
    }

}

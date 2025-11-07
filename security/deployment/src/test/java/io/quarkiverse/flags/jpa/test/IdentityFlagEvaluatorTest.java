package io.quarkiverse.flags.jpa.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Feature;
import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.security.IdentityFlagEvaluator;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.QuarkusUnitTest;

public class IdentityFlagEvaluatorTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.meta.evaluator", IdentityFlagEvaluator.ID)
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.meta.authenticated", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.meta.roles-allowed", "foo,bar");

    @Feature("delta")
    Flag delta;

    @Inject
    CurrentIdentityAssociation identityAssociation;

    @ActivateRequestContext
    @Test
    public void testFlag() {
        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("Foo"))
                .addRole("foo")
                .build());
        assertTrue(delta.isOn());

        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                .setAnonymous(true)
                .build());
        assertFalse(delta.isOn());

        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("Foo"))
                .addRole("baz")
                .addRole("qux")
                .build());
        assertFalse(delta.isOn());
    }

}

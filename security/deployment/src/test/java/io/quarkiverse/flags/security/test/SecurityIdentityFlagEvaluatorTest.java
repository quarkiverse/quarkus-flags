package io.quarkiverse.flags.security.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.RegisterFlag;
import io.quarkiverse.flags.WithEvaluator;
import io.quarkiverse.flags.WithMetadata;
import io.quarkiverse.flags.security.SecurityIdentityFlagEvaluator;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.QuarkusExtensionTest;

public class SecurityIdentityFlagEvaluatorTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(SecurityFlags.class))
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.meta.evaluator", SecurityIdentityFlagEvaluator.ID)
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.meta.authenticated", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.meta.roles-allowed", "foo, bar")
            // roles-allowed with spaces around roles
            .overrideRuntimeConfigKey("quarkus.flags.runtime.echo.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.echo.meta.evaluator", SecurityIdentityFlagEvaluator.ID)
            .overrideRuntimeConfigKey("quarkus.flags.runtime.echo.meta.roles-allowed", " baz , qux ");

    @Inject
    CurrentIdentityAssociation identityAssociation;

    @ActivateRequestContext
    @Test
    public void testFlag() {
        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("Foo"))
                .addRole("foo")
                .build());
        assertTrue(Flag.get("delta").isEnabled());

        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                .setAnonymous(true)
                .build());
        assertFalse(Flag.get("delta").isEnabled());

        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("Foo"))
                .addRole("baz")
                .addRole("qux")
                .build());
        assertFalse(Flag.get("delta").isEnabled());

        // "bar" with leading space in config - should still match after trimming
        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("Foo"))
                .addRole("bar")
                .build());
        assertTrue(Flag.get("delta").isEnabled());
    }

    @ActivateRequestContext
    @Test
    public void testRegisteredFlag() {
        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("Admin"))
                .addRole("admin")
                .build());
        assertTrue(Flag.get("foxtrot").isEnabled());
        // Direct field access - bytecode transformation replaces with Flag.get()
        assertTrue(SecurityFlags.foxtrot);

        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                .setAnonymous(true)
                .build());
        assertFalse(Flag.get("foxtrot").isEnabled());
        assertFalse(SecurityFlags.foxtrot);
    }

    @ActivateRequestContext
    @Test
    public void testRolesWithWhitespace() {
        // Roles in config: " baz , qux " - whitespace should be stripped
        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("Baz"))
                .addRole("baz")
                .build());
        assertTrue(Flag.get("echo").isEnabled());

        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("Qux"))
                .addRole("qux")
                .build());
        assertTrue(Flag.get("echo").isEnabled());

        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("Other"))
                .addRole("other")
                .build());
        assertFalse(Flag.get("echo").isEnabled());
    }

    public static class SecurityFlags {

        @RegisterFlag
        @WithEvaluator(SecurityIdentityFlagEvaluator.ID)
        @WithMetadata(key = SecurityIdentityFlagEvaluator.AUTHENTICATED, value = "true")
        @WithMetadata(key = SecurityIdentityFlagEvaluator.ROLES_ALLOWED, value = "admin")
        static volatile boolean foxtrot = true;
    }

}

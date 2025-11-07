package io.quarkiverse.flags.security;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flag.ComputationContext;
import io.quarkiverse.flags.Flag.Value;
import io.quarkiverse.flags.spi.FlagEvaluator;
import io.quarkiverse.flags.spi.ImmutableBooleanValue;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;

@Singleton
public class IdentityFlagEvaluator implements FlagEvaluator {

    public static final String ID = "quarkus.identity";
    public static final String ROLES_ALLOWED = "roles-allowed";
    public static final String AUTHENTICATED = "authenticated";

    @Inject
    SecurityIdentity identity;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Uni<Value> evaluate(Flag flag, Value initialValue, ComputationContext computationContext) {
        if (initialValue.asBoolean()) {
            String authenticated = flag.metadata().get(AUTHENTICATED);
            if (authenticated != null
                    && Boolean.parseBoolean(authenticated)
                    && identity.isAnonymous()) {
                return Uni.createFrom().item(ImmutableBooleanValue.FALSE);
            }
            String rolesAllowed = flag.metadata().get(ROLES_ALLOWED);
            if (rolesAllowed != null) {
                String[] roles = rolesAllowed.toString().split(",");
                for (String role : roles) {
                    if (identity.hasRole(role)) {
                        return Uni.createFrom().item(ImmutableBooleanValue.TRUE);
                    }
                }
                return Uni.createFrom().item(ImmutableBooleanValue.FALSE);
            }
        }
        return Uni.createFrom().item(initialValue);
    }

}

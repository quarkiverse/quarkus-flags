package io.quarkiverse.flags.security.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkiverse.flags.security.UsernameRolloutFlagEvaluator;
import io.quarkiverse.flags.spi.RolloutFlagEvaluator;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.QuarkusExtensionTest;

public class UsernameRolloutFlagEvaluatorTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withEmptyApplication();

    private static final String FEATURE = "delta";

    @Inject
    CurrentIdentityAssociation identityAssociation;

    @Inject
    InMemoryFlagProvider inMemoryFlagProvider;

    @Inject
    Flags flags;

    @ActivateRequestContext
    @Test
    public void testRollout() {
        Set<String> usernames = new HashSet<>();
        int total = 1000;
        for (int i = 0; i < total; i++) {
            usernames.add(generateUsername());
        }
        inMemoryFlagProvider.addFlag(Flag.builder(FEATURE)
                .setEnabled(true)
                .setMetadata(
                        Map.of("evaluator", UsernameRolloutFlagEvaluator.ID,
                                RolloutFlagEvaluator.ROLLOUT_PERCENTAGE, "10")));
        Set<String> enabledUsernames10 = assertDeltaFlag(usernames);
        // We cannot assert an exact number but it should be a value < 25 percent
        assertTrue(enabledUsernames10.size() < (total / 4));
        inMemoryFlagProvider.removeFlag(FEATURE);
        inMemoryFlagProvider.addFlag(Flag.builder(FEATURE)
                .setEnabled(true)
                .setMetadata(
                        Map.of("evaluator", UsernameRolloutFlagEvaluator.ID,
                                RolloutFlagEvaluator.ROLLOUT_PERCENTAGE, "30")));
        Set<String> enabledUsernames30 = assertDeltaFlag(usernames);
        // We cannot assert an exact number but it should be a value < 50 percent
        assertTrue(enabledUsernames30.size() < (total / 2));
        assertTrue(enabledUsernames30.size() > enabledUsernames10.size());
        // Users enabled in the first round should be enabled in the second round
        assertTrue(enabledUsernames30.containsAll(enabledUsernames10));
    }

    @ActivateRequestContext
    @Test
    public void testRolloutBoundaries() {
        Set<String> usernames = new HashSet<>();
        int total = 500;
        for (int i = 0; i < total; i++) {
            usernames.add(generateUsername());
        }

        // 0% - disabled for everyone
        inMemoryFlagProvider.addFlag(Flag.builder(FEATURE)
                .setEnabled(true)
                .setMetadata(
                        Map.of("evaluator", UsernameRolloutFlagEvaluator.ID,
                                RolloutFlagEvaluator.ROLLOUT_PERCENTAGE, "0")));
        assertTrue(assertDeltaFlag(usernames).isEmpty());
        // 0% - even an anonymous user is disabled
        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder().setAnonymous(true).build());
        assertFalse(flags.findAndAwait(FEATURE).orElseThrow().isEnabled());
        inMemoryFlagProvider.removeFlag(FEATURE);

        // 100% - enabled for everyone
        inMemoryFlagProvider.addFlag(Flag.builder(FEATURE)
                .setEnabled(true)
                .setMetadata(
                        Map.of("evaluator", UsernameRolloutFlagEvaluator.ID,
                                RolloutFlagEvaluator.ROLLOUT_PERCENTAGE, "100")));
        assertEquals(usernames, assertDeltaFlag(usernames));
        // 100% - an anonymous user is enabled too (unlike the 1-99 bucketing)
        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder().setAnonymous(true).build());
        assertTrue(flags.findAndAwait(FEATURE).orElseThrow().isEnabled());
        inMemoryFlagProvider.removeFlag(FEATURE);

        // Out-of-range percentage is rejected
        inMemoryFlagProvider.addFlag(Flag.builder(FEATURE)
                .setEnabled(true)
                .setMetadata(
                        Map.of("evaluator", UsernameRolloutFlagEvaluator.ID,
                                RolloutFlagEvaluator.ROLLOUT_PERCENTAGE, "150")));
        identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(generateUsername()))
                .build());
        Flag invalidFlag = flags.findAndAwait(FEATURE).orElseThrow();
        assertThrows(IllegalStateException.class, invalidFlag::isEnabled);
        inMemoryFlagProvider.removeFlag(FEATURE);
    }

    private Set<String> assertDeltaFlag(Set<String> usernames) {
        Flag flag = flags.findAndAwait(FEATURE).orElseThrow();
        Log.infof("Test with rollout-percentage: %s", flag.metadata().get(RolloutFlagEvaluator.ROLLOUT_PERCENTAGE));
        Set<String> enabledUsernames = new HashSet<>();

        for (String username : usernames) {
            identityAssociation.setIdentity(QuarkusSecurityIdentity.builder()
                    .setPrincipal(new QuarkusPrincipal(username))
                    .build());
            boolean enabled = flag.isEnabled();
            if (enabled) {
                Log.debugf("Username %s enabled", username);
                enabledUsernames.add(username);
            }
            // Verify consistent results
            for (int i = 0; i < 10; i++) {
                assertEquals(enabled, flag.isEnabled());
            }
        }
        Log.infof("Enabled %s from %s generated usernames", enabledUsernames.size(), usernames.size());
        return enabledUsernames;
    }

    private static String generateUsername() {
        String adjective = ADJECTIVES[ThreadLocalRandom.current().nextInt(ADJECTIVES.length)];
        String noun = NOUNS[ThreadLocalRandom.current().nextInt(NOUNS.length)];
        int number = ThreadLocalRandom.current().nextInt(1_000_000);
        return adjective + noun + number;
    }

    private static final String[] ADJECTIVES = {
            "Fierce", "Mystic", "Shadowy", "Eldritch", "Ancient",
            "Cunning", "Ferocious", "Spectral", "Malevolent", "Titanic"
    };

    private static final String[] NOUNS = {
            "Dragon", "Basilisk", "Griffin", "Hydra", "Minotaur",
            "Phantom", "Gorgon", "Goblin", "Wraith", "Chimera"
    };

}

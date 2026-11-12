package io.quarkiverse.flags.cron.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZonedDateTime;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkiverse.flags.RegisterFlag;
import io.quarkiverse.flags.WithEvaluator;
import io.quarkiverse.flags.WithMetadata;
import io.quarkiverse.flags.cron.CronFlagEvaluator;
import io.quarkus.test.QuarkusExtensionTest;

public class CronFlagEvaluatorTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(CronFlags.class));

    @Inject
    InMemoryFlagProvider inMemoryFlagProvider;

    @Inject
    Flags flags;

    @Test
    public void testCronExpr() {
        ZonedDateTime now = ZonedDateTime.now();
        int hour = now.getHour();
        inMemoryFlagProvider.addFlag(Flag.builder("cron")
                .setEnabled(true)
                .setMetadata(Map.of("evaluator", CronFlagEvaluator.ID, "cron-expr", "* %s * * *".formatted(hour))));
        assertTrue(flags.isEnabled("cron"));
        inMemoryFlagProvider.removeFlag("cron");

        int dayOfMonth = now.getDayOfMonth();
        int nonMatchingDay = dayOfMonth == 1 ? 2 : dayOfMonth - 1;
        inMemoryFlagProvider.addFlag(Flag.builder("cron")
                .setEnabled(true)
                .setMetadata(Map.of("evaluator", CronFlagEvaluator.ID, "cron-expr", "* * %s * *".formatted(nonMatchingDay))));
        assertFalse(flags.isEnabled("cron"));
        inMemoryFlagProvider.removeFlag("cron");
    }

    @Test
    public void testCronType() {
        ZonedDateTime now = ZonedDateTime.now();
        int dayOfMonth = now.getDayOfMonth();
        inMemoryFlagProvider.addFlag(Flag.builder("cron")
                .setEnabled(true)
                .setMetadata(Map.of("evaluator", CronFlagEvaluator.ID,
                        "cron-expr", "* * * 1-%s * ?".formatted(dayOfMonth),
                        "cron-type", "QUARTZ")));
        assertTrue(flags.isEnabled("cron"));
        inMemoryFlagProvider.removeFlag("cron");
    }

    @Test
    public void testRegisteredFlag() {
        // "* * * * *" matches every minute -> enabled
        assertTrue(Flag.get("foxtrot").isEnabled());
        assertTrue(CronFlags.foxtrot);
        // "* * 31 2 *" = Feb 31st which never exists -> disabled
        assertFalse(Flag.get("golf").isEnabled());
        assertFalse(CronFlags.golf);
    }

    public static class CronFlags {

        @RegisterFlag
        @WithEvaluator(CronFlagEvaluator.ID)
        @WithMetadata(key = CronFlagEvaluator.CRON_EXPR, value = "* * * * *")
        static volatile boolean foxtrot = true;

        @RegisterFlag
        @WithEvaluator(CronFlagEvaluator.ID)
        @WithMetadata(key = CronFlagEvaluator.CRON_EXPR, value = "* * 31 2 *")
        static volatile boolean golf = true;
    }

}

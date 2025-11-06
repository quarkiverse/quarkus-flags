package io.quarkiverse.flags.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.flags")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface FlagsBuildTimeConfig {

    /**
     * The prefix used to identify feature flag config properties.
     */
    @WithDefault("flag.")
    String configPrefix();

}

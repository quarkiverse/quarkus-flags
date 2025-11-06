package io.quarkiverse.flags.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkiverse.flags.spi.ImmutableFlag;
import io.smallrye.config.SmallRyeConfig;

@Singleton
public class ConfigFlagProvider implements FlagProvider {

    @Inject
    SmallRyeConfig config;

    @Inject
    FlagsBuildTimeConfig buildConfig;

    @Override
    public int getPriority() {
        return FlagProvider.DEFAULT_PRIORITY + 1;
    }

    @Override
    public Iterable<Flag> getFlags() {
        String prefix = buildConfig.configPrefix();
        Set<String> flagPropertyNames = new HashSet<>();
        for (String name : config.getPropertyNames()) {
            if (name.startsWith(prefix)) {
                flagPropertyNames.add(name);
            }
        }

        if (flagPropertyNames.isEmpty()) {
            return List.of();
        }

        List<Flag> ret = new ArrayList<>();
        for (String name : flagPropertyNames) {
            ret.add(new ImmutableFlag(name.substring(prefix.length()), new ConfigState(name)));
        }
        return List.copyOf(ret);
    }

    class ConfigState implements Flag.State {

        final String propertyName;

        ConfigState(String propertyName) {
            this.propertyName = propertyName;
        }

        @Override
        public boolean getBoolean() {
            return config.getOptionalValue(propertyName, Boolean.class).orElseThrow();
        }

        @Override
        public String getString() {
            return config.getOptionalValue(propertyName, String.class).orElseThrow();
        }

        @Override
        public int getInteger() {
            return config.getOptionalValue(propertyName, Integer.class).orElseThrow();
        }

    }

}

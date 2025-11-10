package io.quarkiverse.flags.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.inject.Singleton;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.spi.EvaluatedFlag;
import io.quarkiverse.flags.spi.FlagEvaluator;
import io.quarkiverse.flags.spi.FlagManager;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkiverse.flags.spi.ImmutableFlag;
import io.quarkiverse.flags.spi.ImmutableStringValue;

@Singleton
public class ConfigFlagProvider implements FlagProvider {

    private final FlagManager manager;

    private final FlagsBuildTimeConfig buildConfig;

    private final FlagsRuntimeConfig runtimeConfig;

    public ConfigFlagProvider(FlagManager manager, FlagsBuildTimeConfig buildConfig, FlagsRuntimeConfig runtimeConfig) {
        this.manager = manager;
        this.buildConfig = buildConfig;
        this.runtimeConfig = runtimeConfig;
    }

    @Override
    public int getPriority() {
        return FlagProvider.DEFAULT_PRIORITY + 1;
    }

    @Override
    public Iterable<Flag> getFlags() {
        List<Flag> ret = new ArrayList<>();
        addFlags(ret, buildConfig.flags());
        addFlags(ret, runtimeConfig.flags());
        return List.copyOf(ret);
    }

    private void addFlags(List<Flag> ret, Map<String, FlagConfig> flags) {
        for (Entry<String, FlagConfig> entry : flags.entrySet()) {
            String feature = entry.getKey();
            Map<String, String> metadata = entry.getValue().meta();
            String evaluatorId = metadata.get(FlagEvaluator.METADATA_KEY);
            Flag.Value value = new ImmutableStringValue(entry.getValue().value());
            if (evaluatorId != null) {
                FlagEvaluator evaluator = manager.getEvaluator(evaluatorId).orElseThrow();
                ret.add(new EvaluatedFlag(feature, metadata, value, evaluator));
            } else {
                ret.add(new ImmutableFlag(feature, metadata, value));
            }
        }
    }

}

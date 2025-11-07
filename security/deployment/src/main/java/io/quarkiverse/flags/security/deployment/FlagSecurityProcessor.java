package io.quarkiverse.flags.security.deployment;

import io.quarkiverse.flags.security.IdentityFlagEvaluator;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public class FlagSecurityProcessor {

    @BuildStep
    void beans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(new AdditionalBeanBuildItem(IdentityFlagEvaluator.class));
    }

}

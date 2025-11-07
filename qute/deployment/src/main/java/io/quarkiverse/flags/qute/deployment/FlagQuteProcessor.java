package io.quarkiverse.flags.qute.deployment;

import io.quarkiverse.flags.qute.FlagNamespaceResolver;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public class FlagQuteProcessor {

    @BuildStep
    void beans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(new AdditionalBeanBuildItem(FlagNamespaceResolver.class));
    }

}

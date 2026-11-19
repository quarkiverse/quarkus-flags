package io.quarkiverse.flags.deployment.devui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.flags.deployment.FlagProviderInfoBuildItem;
import io.quarkiverse.flags.runtime.dev.ui.FlagsJsonRPCService;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devjsonrpc.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class FlagsDevUIProcessor {

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    void page(FlagProviderInfoBuildItem providerInfo, BuildProducer<CardPageBuildItem> cardPages) {

        CardPageBuildItem pageBuildItem = new CardPageBuildItem();

        Map<String, Map<String, List<String>>> providerOrdering = new HashMap<>();
        for (String id : providerInfo.getOrderedProviderIds()) {
            Map<String, List<String>> ordering = new HashMap<>();
            ordering.put("before", providerInfo.getBeforeEdges().getOrDefault(id, List.of()));
            ordering.put("after", providerInfo.getAfterEdges().getOrDefault(id, List.of()));
            providerOrdering.put(id, ordering);
        }
        pageBuildItem.addBuildTimeData("providerOrdering", providerOrdering);

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:toggle-on")
                .componentLink("qwc-flags.js"));

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:box")
                .componentLink("qwc-providers.js"));

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:calculator")
                .componentLink("qwc-evaluators.js"));

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:database")
                .componentLink("qwc-cache.js"));

        cardPages.produce(pageBuildItem);
    }

    @BuildStep
    JsonRPCProvidersBuildItem rpcProvider() {
        return new JsonRPCProvidersBuildItem(FlagsJsonRPCService.class);
    }

}

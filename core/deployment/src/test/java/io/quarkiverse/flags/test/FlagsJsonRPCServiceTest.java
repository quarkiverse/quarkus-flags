package io.quarkiverse.flags.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.runtime.impl.InMemoryFlagCache;
import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;
import tools.jackson.databind.JsonNode;

public class FlagsJsonRPCServiceTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(root -> root.addAsResource(new StringAsset("""
                    quarkus.flags.build.alpha.value=true
                    quarkus.flags.build.bravo.value=42
                    quarkus.flags.cache.enabled=true
                    """), "application.properties"));

    public FlagsJsonRPCServiceTest() {
        super("quarkus-flags");
    }

    @Test
    public void testGetFlagsData() throws Exception {
        JsonNode flags = super.executeJsonRPCMethod("getFlagsData");
        assertNotNull(flags);
        assertTrue(flags.isArray());
        assertEquals(2, flags.size());

        JsonNode alpha = findByFeature(flags, "alpha");
        assertNotNull(alpha);
        assertEquals("alpha", alpha.get("feature").asText());
        assertNotNull(alpha.get("origin"));
        assertNotNull(alpha.get("metadata"));

        JsonNode bravo = findByFeature(flags, "bravo");
        assertNotNull(bravo);
        assertEquals("bravo", bravo.get("feature").asText());
    }

    @Test
    public void testComputeValue() throws Exception {
        JsonNode result = super.executeJsonRPCMethod("computeValue", Map.of("feature", "alpha"));
        assertNotNull(result);
        assertEquals("true", result.asText());

        JsonNode bravoResult = super.executeJsonRPCMethod("computeValue", Map.of("feature", "bravo"));
        assertNotNull(bravoResult);
        assertEquals("42", bravoResult.asText());
    }

    @Test
    public void testGetFlagProvidersData() throws Exception {
        JsonNode providers = super.executeJsonRPCMethod("getFlagProvidersData");
        assertNotNull(providers);
        assertTrue(providers.isArray());
        assertTrue(providers.size() >= 1);

        boolean found = false;
        for (int i = 0; i < providers.size(); i++) {
            assertNotNull(providers.get(i).get("id"));
            if (providers.get(i).get("id").asText().contains("config")) {
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testGetProviderFlags() throws Exception {
        JsonNode providers = super.executeJsonRPCMethod("getFlagProvidersData");
        String configProviderId = null;
        for (int i = 0; i < providers.size(); i++) {
            String id = providers.get(i).get("id").asText();
            if (id.contains("config")) {
                configProviderId = id;
                break;
            }
        }
        assertNotNull(configProviderId);

        JsonNode providerFlags = super.executeJsonRPCMethod("getProviderFlags", Map.of("id", configProviderId));
        assertNotNull(providerFlags);
        assertTrue(providerFlags.isArray());
        assertEquals(2, providerFlags.size());
    }

    @Test
    public void testGetFlagEvaluatorsData() throws Exception {
        JsonNode evaluators = super.executeJsonRPCMethod("getFlagEvaluatorsData");
        assertNotNull(evaluators);
        assertTrue(evaluators.isArray());
        assertTrue(evaluators.size() >= 1);

        for (int i = 0; i < evaluators.size(); i++) {
            assertNotNull(evaluators.get(i).get("id"));
            assertNotNull(evaluators.get(i).get("className"));
        }
    }

    @Test
    public void testGetCacheData() throws Exception {
        JsonNode cache = super.executeJsonRPCMethod("getCacheData");
        assertNotNull(cache);
        assertTrue(cache.get("enabled").asBoolean());
        assertNotNull(cache.get("defaultTtl"));
        assertEquals(InMemoryFlagCache.class.getName(), cache.get("implementationClass").asText());

        JsonNode providers = cache.get("providers");
        assertNotNull(providers);
        assertTrue(providers.isArray());
        assertTrue(providers.size() >= 1);
        for (int i = 0; i < providers.size(); i++) {
            assertNotNull(providers.get(i).get("id"));
            assertNotNull(providers.get(i).get("cachingEnabled"));
            assertNotNull(providers.get(i).get("ttl"));
        }
    }

    @Test
    public void testInvalidateCache() throws Exception {
        JsonNode result = super.executeJsonRPCMethod("invalidateCache");
        assertNotNull(result);
        assertTrue(result.asBoolean());
    }

    @Test
    public void testInvalidateProviderCache() throws Exception {
        JsonNode providers = super.executeJsonRPCMethod("getFlagProvidersData");
        String configProviderId = null;
        for (int i = 0; i < providers.size(); i++) {
            String id = providers.get(i).get("id").asText();
            if (id.contains("config")) {
                configProviderId = id;
                break;
            }
        }
        assertNotNull(configProviderId);

        JsonNode result = super.executeJsonRPCMethod("invalidateProviderCache", Map.of("id", configProviderId));
        assertNotNull(result);
        assertTrue(result.asBoolean());
    }

    private static JsonNode findByFeature(JsonNode array, String feature) {
        for (int i = 0; i < array.size(); i++) {
            if (feature.equals(array.get(i).get("feature").asText())) {
                return array.get(i);
            }
        }
        return null;
    }
}

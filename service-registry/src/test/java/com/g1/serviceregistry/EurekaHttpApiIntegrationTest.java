package com.g1.serviceregistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Eureka REST protocol with a plain JDK {@link HttpClient} and hand-written JSON,
 * exactly as a FastAPI, NestJS or Go service would, without any Spring Cloud Eureka client.
 * <p>
 * The read-only response cache is disabled so registry changes are visible immediately on
 * cached endpoints such as {@code GET /eureka/apps}; in a real deployment they become visible
 * within {@code eureka.server.response-cache-update-interval-ms} (30 s by default).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.server.use-read-only-response-cache=false")
class EurekaHttpApiIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void registersServiceInstanceWithMetadata() throws Exception {
        String app = "REG-TEST-SERVICE";
        String id = "reg-test-service:host-a:8080";

        HttpResponse<String> register = register(app, id, 8080, Map.of("framework", "fastapi", "version", "1.2.0"));
        assertThat(register.statusCode()).isEqualTo(204);

        HttpResponse<String> fetched = send("GET", "/eureka/apps/" + app + "/" + id, null);
        assertThat(fetched.statusCode()).isEqualTo(200);

        JsonNode instance = mapper.readTree(fetched.body()).path("instance");
        assertThat(instance.path("instanceId").asText()).isEqualTo(id);
        assertThat(instance.path("app").asText()).isEqualTo(app);
        assertThat(instance.path("status").asText()).isEqualTo("UP");
        assertThat(instance.path("port").path("$").asInt()).isEqualTo(8080);
        assertThat(instance.path("metadata").path("framework").asText()).isEqualTo("fastapi");
        assertThat(instance.path("metadata").path("version").asText()).isEqualTo("1.2.0");

        deregister(app, id);
    }

    @Test
    void heartbeatRenewsLease() throws Exception {
        String app = "HEARTBEAT-TEST-SERVICE";
        String id = "heartbeat-test-service:host-a:8081";
        assertThat(register(app, id, 8081, Map.of()).statusCode()).isEqualTo(204);

        long before = lastRenewalTimestamp(app, id);
        Thread.sleep(50);

        HttpResponse<String> heartbeat = send("PUT", "/eureka/apps/" + app + "/" + id, null);
        assertThat(heartbeat.statusCode()).isEqualTo(200);

        assertThat(lastRenewalTimestamp(app, id)).isGreaterThan(before);

        deregister(app, id);
    }

    @Test
    void heartbeatForUnknownInstanceReturnsNotFound() throws Exception {
        // A 404 tells the client it must re-register (e.g. after registry restart or eviction).
        HttpResponse<String> heartbeat = send("PUT", "/eureka/apps/UNKNOWN-SERVICE/does-not-exist", null);
        assertThat(heartbeat.statusCode()).isEqualTo(404);
    }

    @Test
    void deregistrationRemovesInstance() throws Exception {
        String app = "DEREG-TEST-SERVICE";
        String id = "dereg-test-service:host-a:8082";
        assertThat(register(app, id, 8082, Map.of()).statusCode()).isEqualTo(204);

        HttpResponse<String> delete = send("DELETE", "/eureka/apps/" + app + "/" + id, null);
        assertThat(delete.statusCode()).isEqualTo(200);

        assertThat(send("GET", "/eureka/apps/" + app + "/" + id, null).statusCode()).isEqualTo(404);
        assertThat(send("PUT", "/eureka/apps/" + app + "/" + id, null).statusCode()).isEqualTo(404);
    }

    @Test
    void registeredServiceIsDiscoverable() throws Exception {
        String app = "DISCOVERY-TEST-SERVICE";
        String id = "discovery-test-service:host-a:8083";
        assertThat(register(app, id, 8083, Map.of()).statusCode()).isEqualTo(204);

        // Full registry
        HttpResponse<String> all = send("GET", "/eureka/apps", null);
        assertThat(all.statusCode()).isEqualTo(200);
        List<String> appNames = new ArrayList<>();
        asList(mapper.readTree(all.body()).path("applications").path("application"))
                .forEach(node -> appNames.add(node.path("name").asText()));
        assertThat(appNames).contains(app);

        // Single application
        HttpResponse<String> one = send("GET", "/eureka/apps/" + app, null);
        assertThat(one.statusCode()).isEqualTo(200);
        assertThat(instanceIds(mapper.readTree(one.body()).path("application"))).containsExactly(id);

        // Lookup by VIP address (logical service name)
        HttpResponse<String> byVip = send("GET", "/eureka/vips/" + app.toLowerCase(), null);
        assertThat(byVip.statusCode()).isEqualTo(200);
        assertThat(byVip.body()).contains(id);

        deregister(app, id);
    }

    @Test
    void multipleInstancesShareOneServiceName() throws Exception {
        String app = "MULTI-TEST-SERVICE";
        String id1 = "multi-test-service:host-a:9001";
        String id2 = "multi-test-service:host-b:9002";
        assertThat(register(app, id1, 9001, Map.of("zone", "a")).statusCode()).isEqualTo(204);
        assertThat(register(app, id2, 9002, Map.of("zone", "b")).statusCode()).isEqualTo(204);

        HttpResponse<String> response = send("GET", "/eureka/apps/" + app, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(instanceIds(mapper.readTree(response.body()).path("application")))
                .containsExactlyInAnyOrder(id1, id2);

        // Removing one instance leaves the other registered
        deregister(app, id1);
        HttpResponse<String> after = send("GET", "/eureka/apps/" + app, null);
        assertThat(instanceIds(mapper.readTree(after.body()).path("application"))).containsExactly(id2);

        deregister(app, id2);
    }

    // ------------------------------------------------------------------ helpers

    private HttpResponse<String> register(String app, String instanceId, int instancePort,
                                          Map<String, String> metadata) throws Exception {
        String vip = app.toLowerCase();
        String baseUrl = "http://127.0.0.1:" + instancePort;

        Map<String, Object> instance = new LinkedHashMap<>();
        instance.put("instanceId", instanceId);
        instance.put("hostName", "127.0.0.1");
        instance.put("app", app);
        instance.put("ipAddr", "127.0.0.1");
        instance.put("vipAddress", vip);
        instance.put("secureVipAddress", vip);
        instance.put("status", "UP");
        instance.put("port", Map.of("$", instancePort, "@enabled", "true"));
        instance.put("securePort", Map.of("$", 443, "@enabled", "false"));
        instance.put("homePageUrl", baseUrl + "/");
        instance.put("statusPageUrl", baseUrl + "/info");
        instance.put("healthCheckUrl", baseUrl + "/health");
        instance.put("dataCenterInfo", Map.of(
                "@class", "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
                "name", "MyOwn"));
        instance.put("leaseInfo", Map.of("renewalIntervalInSecs", 30, "durationInSecs", 90));
        instance.put("metadata", metadata);

        String body = mapper.writeValueAsString(Map.of("instance", instance));
        return send("POST", "/eureka/apps/" + app, body);
    }

    private void deregister(String app, String instanceId) throws Exception {
        assertThat(send("DELETE", "/eureka/apps/" + app + "/" + instanceId, null).statusCode()).isEqualTo(200);
    }

    private long lastRenewalTimestamp(String app, String instanceId) throws Exception {
        HttpResponse<String> response = send("GET", "/eureka/apps/" + app + "/" + instanceId, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body()).path("instance").path("leaseInfo")
                .path("lastRenewalTimestamp").asLong();
    }

    private List<String> instanceIds(JsonNode application) {
        List<String> ids = new ArrayList<>();
        asList(application.path("instance")).forEach(node -> ids.add(node.path("instanceId").asText()));
        return ids;
    }

    /** Eureka JSON may render a single-element collection as an object rather than an array. */
    private static List<JsonNode> asList(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(result::add);
        } else if (node.isObject()) {
            result.add(node);
        }
        return result;
    }

    private HttpResponse<String> send(String method, String path, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json");
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}

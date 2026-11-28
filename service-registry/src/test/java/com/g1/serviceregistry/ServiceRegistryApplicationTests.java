package com.g1.serviceregistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Eureka Server starts and that management endpoints are reachable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceRegistryApplicationTests {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private PeerAwareInstanceRegistry registry;

    @Test
    void eurekaServerStartsWithInstanceRegistry() {
        assertThat(registry).isNotNull();
    }

    @Test
    void dashboardIsServed() throws Exception {
        HttpResponse<String> response = get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).containsIgnoringCase("Eureka");
    }

    @Test
    void eurekaRestApiIsServed() throws Exception {
        HttpResponse<String> response = get("/eureka/apps");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).has("applications")).isTrue();
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.path("status").asText()).isEqualTo("UP");
    }

    @Test
    void livenessAndReadinessProbesAreAvailable() throws Exception {
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
    }

    @Test
    void infoEndpointIsAvailable() throws Exception {
        HttpResponse<String> response = get("/actuator/info");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).path("app").path("name").asText())
                .isEqualTo("service-registry");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

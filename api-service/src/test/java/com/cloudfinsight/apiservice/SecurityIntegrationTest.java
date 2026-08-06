package com.cloudfinsight.apiservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack security tests against a real, isolated Keycloak instance (Testcontainers),
 * imported from the same realm export used by the dev docker-compose setup - one source
 * of truth for roles/users/audience mapper, per this project's real-infra-over-mocks
 * convention (established in Epic 5's RabbitMQ TestContainers test).
 *
 * Deliberately NOT a @WebMvcTest slice: this exercises the real SecurityFilterChain,
 * real JwtDecoder issuer/audience validation, and real role-to-authority mapping against
 * genuine tokens obtained via password grant - nothing here is mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class SecurityIntegrationTest {

    @Container
    static final KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.6")
        .withRealmImportFile("cloud-finsight-realm.json");

    @DynamicPropertySource
    static void registerKeycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> keycloak.getAuthServerUrl() + "/realms/cloud-finsight");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private static String viewerToken;
    private static String adminToken;

    @BeforeAll
    static void obtainTokens() {
        viewerToken = fetchToken("dashboard-user", "Password123!");
        adminToken = fetchToken("admin-user", "Password123!");
    }

    // Plain RestTemplate with an explicit, simple request factory - avoids the
    // ClientHttpRequestFactoryBuilder auto-detection path entirely, since this
    // is a static helper running before Spring's context (and its autoconfigured
    // TestRestTemplate bean) is fully available anyway.
    private static String fetchToken(String username, String password) {
        RestTemplate tokenClient = new RestTemplate(new SimpleClientHttpRequestFactory());
        String tokenUrl = keycloak.getAuthServerUrl() + "/realms/cloud-finsight/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", "cloud-finsight-api");
        form.add("client_secret", "dev-client-secret-change-me");
        form.add("grant_type", "password");
        form.add("username", username);
        form.add("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        ResponseEntity<String> response = tokenClient.postForEntity(tokenUrl, request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        try {
            JsonNode json = new ObjectMapper().readTree(response.getBody());
            return json.get("access_token").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse token response: " + response.getBody(), e);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpEntity<Void> authorizedRequest(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(headers);
    }

    @Test
    void noToken_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl() + "/api/v1/vms", HttpMethod.GET, authorizedRequest(null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void viewerToken_getVms_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl() + "/api/v1/vms", HttpMethod.GET, authorizedRequest(viewerToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void viewerToken_deleteRecommendation_returns403() {
        // id doesn't need to exist - authorization is checked before the handler runs,
        // so a 403 here proves the role check itself, independent of whether the row exists.
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl() + "/api/v1/recommendations/999999", HttpMethod.DELETE, authorizedRequest(viewerToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminToken_deleteRecommendation_isAuthorized_notForbidden() {
        // Using a near-certainly-nonexistent id: the point of this test is proving the
        // admin role clears the authorization check (no 403), not exercising delete
        // semantics on real data - that's covered by RecommendationService's own tests.
        // A 404 here is success; a 403 would mean role mapping is broken.
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl() + "/api/v1/recommendations/999999", HttpMethod.DELETE, authorizedRequest(adminToken), String.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

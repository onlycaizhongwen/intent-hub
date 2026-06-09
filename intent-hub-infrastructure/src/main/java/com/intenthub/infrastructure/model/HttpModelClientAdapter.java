package com.intenthub.infrastructure.model;

import com.intenthub.domain.config.ModelPolicy;
import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.policy.ModelClientPort;
import com.intenthub.domain.recognition.policy.ModelServiceHealth;
import com.intenthub.domain.recognition.policy.ModelServiceAuthenticationException;
import com.intenthub.infrastructure.http.ExternalRestClients;
import com.intenthub.infrastructure.security.EnvironmentSecretRefResolver;
import com.intenthub.infrastructure.security.SecretRefResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class HttpModelClientAdapter implements ModelClientPort {
    private final RestClient restClient;
    private final RestClient.Builder restClientBuilder;
    private final ModelServiceProperties properties;
    private final SecretRefResolver secretRefResolver;
    private final Map<ClientKey, RestClient> routedClients = new ConcurrentHashMap<>();

    public HttpModelClientAdapter(RestClient.Builder restClientBuilder, ModelServiceProperties properties) {
        this(restClientBuilder, properties, new EnvironmentSecretRefResolver());
    }

    public HttpModelClientAdapter(RestClient.Builder restClientBuilder, ModelServiceProperties properties, SecretRefResolver secretRefResolver) {
        this(ExternalRestClients.build(restClientBuilder, properties.baseUrl(), properties.timeoutMs()), restClientBuilder, properties, secretRefResolver);
    }

    HttpModelClientAdapter(RestClient restClient, ModelServiceProperties properties) {
        this(restClient, null, properties, new EnvironmentSecretRefResolver());
    }

    HttpModelClientAdapter(RestClient restClient, RestClient.Builder restClientBuilder, ModelServiceProperties properties) {
        this(restClient, restClientBuilder, properties, new EnvironmentSecretRefResolver());
    }

    HttpModelClientAdapter(RestClient restClient, RestClient.Builder restClientBuilder, ModelServiceProperties properties, SecretRefResolver secretRefResolver) {
        this.restClient = restClient;
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
        this.secretRefResolver = secretRefResolver == null ? new EnvironmentSecretRefResolver() : secretRefResolver;
    }

    @Override
    public Optional<RecognitionCandidate> recognize(String text, String sceneId) {
        return recognize(text, sceneId, null);
    }

    @Override
    public Optional<RecognitionCandidate> recognize(String text, String sceneId, ModelPolicy policy) {
        if (!properties.enabled() || endpoint(policy).isBlank()) {
            return Optional.empty();
        }
        RestClient client = clientFor(policy);
        ModelRecognitionResponse response = client.post()
                .uri("/recognize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ModelRecognitionRequest(text, sceneId))
                .retrieve()
                .body(ModelRecognitionResponse.class);
        if (response == null || response.intentCode() == null || response.intentCode().isBlank() || response.confidence() == null) {
            return Optional.empty();
        }
        return Optional.of(new RecognitionCandidate(
                response.intentCode(),
                response.confidence(),
                response.slots() == null ? Map.of() : response.slots(),
                response.explanation() == null ? "model service candidate" : response.explanation()
        ));
    }

    @Override
    public boolean healthy() {
        return healthDetails().healthy();
    }

    @Override
    public ModelServiceHealth healthDetails() {
        if (!properties.active()) {
            return ModelServiceHealth.down();
        }
        try {
            ModelHealthResponse response = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(ModelHealthResponse.class);
            if (response == null || !"UP".equalsIgnoreCase(response.status())) {
                return ModelServiceHealth.down();
            }
            return new ModelServiceHealth(true, response.modelVersion(), response.threshold());
        } catch (RestClientException ex) {
            return ModelServiceHealth.down();
        }
    }

    private RestClient clientFor(ModelPolicy policy) {
        if (policy == null || restClientBuilder == null || !overridesGlobalClient(policy)) {
            return restClient;
        }
        ClientRoute route = clientRoute(policy);
        evictRotatedClients(route.key());
        return routedClients.computeIfAbsent(route.key(), current ->
                buildRoutedClient(route));
    }

    int cachedClientCount() {
        return routedClients.size();
    }

    private boolean overridesGlobalClient(ModelPolicy policy) {
        return !policy.endpoint().isBlank() || policy.timeoutMs() > 0 || !policy.authTokenRef().isBlank();
    }

    private void evictRotatedClients(ClientKey current) {
        routedClients.keySet().removeIf(existing -> existing.sameRoute(current) && !existing.authTokenFingerprint().equals(current.authTokenFingerprint()));
    }

    private RestClient buildRoutedClient(ClientRoute route) {
        RestClient.Builder builder = restClientBuilder.clone();
        if (!route.authToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + route.authToken());
        }
        return ExternalRestClients.build(builder, route.key().endpoint(), route.key().timeoutMs());
    }

    private String endpoint(ModelPolicy policy) {
        if (policy != null && !policy.endpoint().isBlank()) {
            return policy.endpoint();
        }
        return properties.baseUrl();
    }

    private int timeoutMs(ModelPolicy policy) {
        if (policy.timeoutMs() > 0) {
            return policy.timeoutMs();
        }
        return properties.timeoutMs();
    }

    private String authToken(ModelPolicy policy) {
        if (policy == null || policy.authTokenRef().isBlank()) {
            return "";
        }
        return secretRefResolver.resolve(policy.authTokenRef()).orElse("");
    }

    private ClientRoute clientRoute(ModelPolicy policy) {
        String token = authToken(policy);
        if (!policy.authTokenRef().isBlank() && token.isBlank()) {
            throw new ModelServiceAuthenticationException("MODEL_FALLBACK:AUTH_MISSING_TOKEN");
        }
        ClientKey key = new ClientKey(
                endpoint(policy),
                timeoutMs(policy),
                policy.authTokenRef().trim(),
                tokenFingerprint(token)
        );
        return new ClientRoute(key, token);
    }

    private String tokenFingerprint(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    private record ClientRoute(ClientKey key, String authToken) {
    }

    private record ClientKey(String endpoint, int timeoutMs, String authTokenRef, String authTokenFingerprint) {
        private boolean sameRoute(ClientKey other) {
            return endpoint.equals(other.endpoint)
                    && timeoutMs == other.timeoutMs
                    && authTokenRef.equals(other.authTokenRef);
        }
    }
}

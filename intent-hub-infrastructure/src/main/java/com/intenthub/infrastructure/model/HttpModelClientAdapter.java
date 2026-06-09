package com.intenthub.infrastructure.model;

import com.intenthub.domain.config.ModelPolicy;
import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.policy.ModelClientPort;
import com.intenthub.domain.recognition.policy.ModelServiceHealth;
import com.intenthub.domain.recognition.policy.ModelServiceAuthenticationException;
import com.intenthub.infrastructure.http.ExternalRestClients;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class HttpModelClientAdapter implements ModelClientPort {
    private final RestClient restClient;
    private final RestClient.Builder restClientBuilder;
    private final ModelServiceProperties properties;
    private final Map<ClientKey, RestClient> routedClients = new ConcurrentHashMap<>();

    public HttpModelClientAdapter(RestClient.Builder restClientBuilder, ModelServiceProperties properties) {
        this(ExternalRestClients.build(restClientBuilder, properties.baseUrl(), properties.timeoutMs()), restClientBuilder, properties);
    }

    HttpModelClientAdapter(RestClient restClient, ModelServiceProperties properties) {
        this(restClient, null, properties);
    }

    HttpModelClientAdapter(RestClient restClient, RestClient.Builder restClientBuilder, ModelServiceProperties properties) {
        this.restClient = restClient;
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
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
        ClientKey key = new ClientKey(endpoint(policy), timeoutMs(policy), authTokenOrThrow(policy));
        return routedClients.computeIfAbsent(key, current ->
                buildRoutedClient(current));
    }

    int cachedClientCount() {
        return routedClients.size();
    }

    private boolean overridesGlobalClient(ModelPolicy policy) {
        return !policy.endpoint().isBlank() || policy.timeoutMs() > 0 || !policy.authTokenRef().isBlank();
    }

    private RestClient buildRoutedClient(ClientKey key) {
        RestClient.Builder builder = restClientBuilder.clone();
        if (!key.authToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key.authToken());
        }
        return ExternalRestClients.build(builder, key.endpoint(), key.timeoutMs());
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
        String systemPropertyValue = System.getProperty(policy.authTokenRef());
        if (systemPropertyValue != null && !systemPropertyValue.isBlank()) {
            return systemPropertyValue;
        }
        String environmentValue = System.getenv(policy.authTokenRef());
        return environmentValue == null ? "" : environmentValue;
    }

    private String authTokenOrThrow(ModelPolicy policy) {
        String token = authToken(policy);
        if (!policy.authTokenRef().isBlank() && token.isBlank()) {
            throw new ModelServiceAuthenticationException("MODEL_FALLBACK:AUTH_MISSING_TOKEN");
        }
        return token;
    }

    private record ClientKey(String endpoint, int timeoutMs, String authToken) {
    }
}

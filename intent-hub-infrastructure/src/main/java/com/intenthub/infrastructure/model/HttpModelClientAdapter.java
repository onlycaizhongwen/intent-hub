package com.intenthub.infrastructure.model;

import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.policy.ModelClientPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

public class HttpModelClientAdapter implements ModelClientPort {
    private final RestClient restClient;
    private final ModelServiceProperties properties;

    public HttpModelClientAdapter(RestClient.Builder restClientBuilder, ModelServiceProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .build();
        this.properties = properties;
    }

    @Override
    public Optional<RecognitionCandidate> recognize(String text, String sceneId) {
        if (!properties.active()) {
            return Optional.empty();
        }
        ModelRecognitionResponse response = restClient.post()
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
}

package com.intenthub.interfaces.admin;

import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.policy.ModelClientPort;
import com.intenthub.domain.recognition.policy.ModelServiceHealth;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdminHealthControllerTest {
    @Test
    void exposesCoreAndModelServiceHealth() {
        AdminHealthController controller = new AdminHealthController(new HealthyModelClient());

        assertThat(controller.health())
                .containsEntry("status", "UP")
                .containsEntry("scope", "p1-minimal-loop");
        assertThat(controller.health().get("model_service")).isEqualTo(java.util.Map.of(
                "healthy", true,
                "modelVersion", "example-v1",
                "threshold", 0.7
        ));
    }

    private static final class HealthyModelClient implements ModelClientPort {
        @Override
        public Optional<RecognitionCandidate> recognize(String text, String sceneId) {
            return Optional.empty();
        }

        @Override
        public boolean healthy() {
            return true;
        }

        @Override
        public ModelServiceHealth healthDetails() {
            return new ModelServiceHealth(true, "example-v1", 0.7);
        }
    }
}

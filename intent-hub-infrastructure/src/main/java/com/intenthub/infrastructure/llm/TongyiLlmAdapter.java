package com.intenthub.infrastructure.llm;

import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.policy.LlmClientPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TongyiLlmAdapter implements LlmClientPort {
    @Override
    public Optional<RecognitionCandidate> recognize(String text, String sceneId) {
        // P1 keeps LLM as a governed fallback. Real Spring AI Alibaba calls are wired after policy gates are stable.
        return Optional.empty();
    }
}

package com.intenthub.infrastructure.config;

import com.intenthub.application.BadCasePort;
import com.intenthub.application.IdempotencyPort;
import com.intenthub.application.RecognitionTracePort;
import com.intenthub.application.RecognizeAppService;
import com.intenthub.application.SceneConfigPort;
import com.intenthub.application.config.AuditLogPort;
import com.intenthub.application.config.ConfigVersionAppService;
import com.intenthub.application.config.ConfigVersionPort;
import com.intenthub.domain.recognition.policy.LlmClientPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntentHubBeanConfiguration {
    @Bean
    RecognizeAppService recognizeAppService(
            SceneConfigPort sceneConfigPort,
            RecognitionTracePort recognitionTracePort,
            BadCasePort badCasePort,
            IdempotencyPort idempotencyPort,
            LlmClientPort llmClientPort
    ) {
        return new RecognizeAppService(
                sceneConfigPort,
                recognitionTracePort,
                badCasePort,
                idempotencyPort,
                llmClientPort
        );
    }

    @Bean
    ConfigVersionAppService configVersionAppService(
            ConfigVersionPort configVersionPort,
            AuditLogPort auditLogPort
    ) {
        return new ConfigVersionAppService(configVersionPort, auditLogPort);
    }
}

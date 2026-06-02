package com.intenthub.infrastructure.config;

import com.intenthub.application.BadCasePort;
import com.intenthub.application.IdempotencyPort;
import com.intenthub.application.RecognitionTracePort;
import com.intenthub.application.RecognizeAppService;
import com.intenthub.application.SceneConfigPort;
import com.intenthub.application.config.AuditLogPort;
import com.intenthub.application.config.ConfigObjectAppService;
import com.intenthub.application.config.ConfigObjectPort;
import com.intenthub.application.config.ConfigVersionAppService;
import com.intenthub.application.config.ConfigVersionPort;
import com.intenthub.application.metrics.IntentMetricsPort;
import com.intenthub.application.metrics.MetricsAppService;
import com.intenthub.application.observability.BadCaseWorkflowAppService;
import com.intenthub.application.observability.BadCaseWorkflowPort;
import com.intenthub.application.observability.ObservabilityAppService;
import com.intenthub.application.observability.ObservabilityQueryPort;
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
            LlmClientPort llmClientPort,
            IntentMetricsPort metricsPort
    ) {
        return new RecognizeAppService(
                sceneConfigPort,
                recognitionTracePort,
                badCasePort,
                idempotencyPort,
                llmClientPort,
                metricsPort
        );
    }

    @Bean
    ConfigVersionAppService configVersionAppService(
            ConfigVersionPort configVersionPort,
            AuditLogPort auditLogPort
    ) {
        return new ConfigVersionAppService(configVersionPort, auditLogPort);
    }

    @Bean
    ConfigObjectAppService configObjectAppService(
            ConfigVersionPort configVersionPort,
            ConfigObjectPort configObjectPort,
            AuditLogPort auditLogPort
    ) {
        return new ConfigObjectAppService(configVersionPort, configObjectPort, auditLogPort);
    }

    @Bean
    ObservabilityAppService observabilityAppService(ObservabilityQueryPort observabilityQueryPort) {
        return new ObservabilityAppService(observabilityQueryPort);
    }

    @Bean
    BadCaseWorkflowAppService badCaseWorkflowAppService(BadCaseWorkflowPort badCaseWorkflowPort) {
        return new BadCaseWorkflowAppService(badCaseWorkflowPort);
    }

    @Bean
    MetricsAppService metricsAppService(IntentMetricsPort metricsPort) {
        return new MetricsAppService(metricsPort);
    }
}

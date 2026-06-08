package com.intenthub.infrastructure.config;

import com.intenthub.application.BadCasePort;
import com.intenthub.application.IdempotencyPort;
import com.intenthub.application.RecognitionTracePort;
import com.intenthub.application.RecognizeAppService;
import com.intenthub.application.SceneConfigPort;
import com.intenthub.application.config.AuditLogPort;
import com.intenthub.application.config.ConfigAuditAppService;
import com.intenthub.application.config.ConfigObjectAppService;
import com.intenthub.application.config.ConfigObjectPort;
import com.intenthub.application.config.ConfigVersionAppService;
import com.intenthub.application.config.ConfigVersionPort;
import com.intenthub.application.llm.LlmBudgetAuditPort;
import com.intenthub.application.llm.LlmBudgetAppService;
import com.intenthub.application.metrics.IntentMetricsPort;
import com.intenthub.application.metrics.MetricsAlertAppService;
import com.intenthub.application.metrics.MetricsAppService;
import com.intenthub.application.observability.BadCaseWorkflowAppService;
import com.intenthub.application.observability.BadCaseWorkflowPort;
import com.intenthub.application.observability.ObservabilityAppService;
import com.intenthub.application.observability.ObservabilityQueryPort;
import com.intenthub.domain.recognition.policy.LlmClientPort;
import com.intenthub.domain.recognition.policy.ModelClientPort;
import com.intenthub.infrastructure.llm.LlmGovernanceProperties;
import com.intenthub.infrastructure.llm.LlmBudgetReconciliationProperties;
import com.intenthub.infrastructure.llm.TongyiLlmAdapter;
import com.intenthub.infrastructure.model.HttpModelClientAdapter;
import com.intenthub.infrastructure.model.ModelServiceProperties;
import com.intenthub.infrastructure.model.NoopModelClientAdapter;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({ModelServiceProperties.class, LlmGovernanceProperties.class, LlmBudgetReconciliationProperties.class})
public class IntentHubBeanConfiguration {
    @Bean
    RecognizeAppService recognizeAppService(
            SceneConfigPort sceneConfigPort,
            RecognitionTracePort recognitionTracePort,
            BadCasePort badCasePort,
            IdempotencyPort idempotencyPort,
            LlmClientPort llmClientPort,
            IntentMetricsPort metricsPort,
            ModelClientPort modelClientPort
    ) {
        return new RecognizeAppService(
                sceneConfigPort,
                recognitionTracePort,
                badCasePort,
                idempotencyPort,
                llmClientPort,
                metricsPort,
                modelClientPort
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
    ConfigAuditAppService configAuditAppService(AuditLogPort auditLogPort) {
        return new ConfigAuditAppService(auditLogPort);
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

    @Bean
    MetricsAlertAppService metricsAlertAppService(IntentMetricsPort metricsPort) {
        return new MetricsAlertAppService(metricsPort);
    }

    @Bean
    LlmBudgetAppService llmBudgetAppService(LlmBudgetAuditPort budgetAuditPort) {
        return new LlmBudgetAppService(budgetAuditPort);
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    ModelClientPort modelClientPort(RestClient.Builder restClientBuilder, ModelServiceProperties properties) {
        if (!properties.active()) {
            return new NoopModelClientAdapter();
        }
        return new HttpModelClientAdapter(restClientBuilder, properties);
    }

    @Bean
    LlmClientPort llmClientPort(
            RestClient.Builder restClientBuilder,
            ListableBeanFactory beanFactory,
            LlmGovernanceProperties properties,
            IntentMetricsPort metricsPort,
            LlmBudgetAuditPort budgetAuditPort
    ) {
        return new TongyiLlmAdapter(restClientBuilder, springAiChatClientBuilder(beanFactory), properties, metricsPort, budgetAuditPort);
    }

    private Object springAiChatClientBuilder(ListableBeanFactory beanFactory) {
        try {
            Class<?> builderType = Class.forName("org.springframework.ai.chat.client.ChatClient$Builder");
            String[] beanNames = beanFactory.getBeanNamesForType(builderType);
            if (beanNames.length == 0) {
                return null;
            }
            return beanFactory.getBean(beanNames[0]);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }
}

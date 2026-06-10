package com.intenthub.infrastructure.llm;

import com.intenthub.application.metrics.IntentMetricsPort;
import com.intenthub.application.metrics.MetricsSnapshot;
import com.intenthub.application.llm.LlmBudgetAuditPort;
import com.intenthub.application.llm.LlmBudgetUsage;
import com.intenthub.domain.config.LlmPolicy;
import com.intenthub.domain.recognition.RecognitionCandidate;
import com.intenthub.domain.recognition.policy.LlmClientPort;
import com.intenthub.infrastructure.http.ExternalRestClients;
import com.intenthub.infrastructure.security.EnvironmentSecretRefResolver;
import com.intenthub.infrastructure.security.SecretRefResolver;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

public class TongyiLlmAdapter implements LlmClientPort {
    private final RestClient restClient;
    private final Object chatClient;
    private final LlmGovernanceProperties properties;
    private final IntentMetricsPort metricsPort;
    private final LlmBudgetAuditPort budgetAuditPort;
    private final SecretRefResolver secretRefResolver;

    public TongyiLlmAdapter(RestClient.Builder restClientBuilder, LlmGovernanceProperties properties, IntentMetricsPort metricsPort) {
        this(restClientBuilder, null, properties, metricsPort, new NoopLlmBudgetAuditPort(), new EnvironmentSecretRefResolver());
    }

    public TongyiLlmAdapter(
            RestClient.Builder restClientBuilder,
            Object chatClientBuilder,
            LlmGovernanceProperties properties,
            IntentMetricsPort metricsPort,
            LlmBudgetAuditPort budgetAuditPort
    ) {
        this(restClientBuilder, chatClientBuilder, properties, metricsPort, budgetAuditPort, new EnvironmentSecretRefResolver());
    }

    public TongyiLlmAdapter(
            RestClient.Builder restClientBuilder,
            Object chatClientBuilder,
            LlmGovernanceProperties properties,
            IntentMetricsPort metricsPort,
            LlmBudgetAuditPort budgetAuditPort,
            SecretRefResolver secretRefResolver
    ) {
        this(
                ExternalRestClients.build(restClientBuilder, properties.baseUrl(), properties.timeoutMs()),
                buildChatClient(chatClientBuilder),
                properties,
                metricsPort,
                budgetAuditPort,
                secretRefResolver
        );
    }

    TongyiLlmAdapter(RestClient restClient, LlmGovernanceProperties properties) {
        this(restClient, null, properties, new NoopIntentMetricsPort(), new NoopLlmBudgetAuditPort(), new EnvironmentSecretRefResolver());
    }

    TongyiLlmAdapter(RestClient restClient, LlmGovernanceProperties properties, IntentMetricsPort metricsPort) {
        this(restClient, null, properties, metricsPort, new NoopLlmBudgetAuditPort(), new EnvironmentSecretRefResolver());
    }

    TongyiLlmAdapter(RestClient restClient, Object chatClient, LlmGovernanceProperties properties, IntentMetricsPort metricsPort) {
        this(restClient, chatClient, properties, metricsPort, new NoopLlmBudgetAuditPort(), new EnvironmentSecretRefResolver());
    }

    TongyiLlmAdapter(RestClient restClient, Object chatClient, LlmGovernanceProperties properties, IntentMetricsPort metricsPort, LlmBudgetAuditPort budgetAuditPort) {
        this(restClient, chatClient, properties, metricsPort, budgetAuditPort, new EnvironmentSecretRefResolver());
    }

    TongyiLlmAdapter(
            RestClient restClient,
            Object chatClient,
            LlmGovernanceProperties properties,
            IntentMetricsPort metricsPort,
            LlmBudgetAuditPort budgetAuditPort,
            SecretRefResolver secretRefResolver
    ) {
        this.restClient = restClient;
        this.chatClient = chatClient;
        this.properties = properties;
        this.metricsPort = metricsPort == null ? new NoopIntentMetricsPort() : metricsPort;
        this.budgetAuditPort = budgetAuditPort == null ? new NoopLlmBudgetAuditPort() : budgetAuditPort;
        this.secretRefResolver = secretRefResolver == null ? new EnvironmentSecretRefResolver() : secretRefResolver;
    }

    @Override
    public Optional<RecognitionCandidate> recognize(String text, String tenantId, String sceneId, LlmPolicy policy) {
        if (!active(policy)) {
            return Optional.empty();
        }
        RuntimeException lastFailure = null;
        int attempts = Math.min(policy.maxRetries(), properties.maxRetries()) + 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            boolean reserved = false;
            try {
                if (!reserveBudget(tenantId, sceneId, policy)) {
                    return Optional.empty();
                }
                reserved = true;
                metricsPort.recordLlmBudgetConsumption(1.0);
                budgetAuditPort.recordAttempt(tenantId, sceneId, policy.provider(), policy.model(), 1.0);
                LlmRecognitionResponse response = recognizeByProvider(text, sceneId, policy);
                return candidate(response);
            } catch (RuntimeException ex) {
                if (reserved) {
                    budgetAuditPort.releaseDailyBudgetReservation(tenantId, sceneId, policy.provider(), policy.model(), 1.0);
                }
                lastFailure = ex;
            }
        }
        throw lastFailure == null ? new IllegalStateException("LLM request failed") : lastFailure;
    }

    private boolean active(LlmPolicy policy) {
        return properties.active()
                && policy.enabled()
                && policy.dailyBudget() > 0.0
                && policy.timeoutMs() > 0;
    }

    private boolean reserveBudget(String tenantId, String sceneId, LlmPolicy policy) {
        double effectiveBudget = Math.min(properties.dailyBudget(), policy.dailyBudget());
        return budgetAuditPort.tryReserveDailyBudget(tenantId, sceneId, policy.provider(), policy.model(), 1.0, effectiveBudget);
    }

    Optional<String> resolveSecretRef(String ref) {
        return secretRefResolver.resolve(ref);
    }

    private LlmRecognitionResponse recognizeByProvider(String text, String sceneId, LlmPolicy policy) {
        if (chatClient != null && "spring-ai-alibaba".equalsIgnoreCase(policy.provider())) {
            return recognizeBySpringAi(text, sceneId, policy);
        }
        return restClient.post()
                .uri("/recognize")
                .body(new LlmRecognitionRequest(text, sceneId, policy.provider(), policy.model()))
                .retrieve()
                .body(LlmRecognitionResponse.class);
    }

    private LlmRecognitionResponse recognizeBySpringAi(String text, String sceneId, LlmPolicy policy) {
        try {
            Object requestSpec = invoke(chatClient, "prompt");
            requestSpec = invoke(requestSpec, "system", """
                    You are the controlled fallback recognizer for IntentHub.
                    Return only structured data matching this schema:
                    intentCode string, confidence number from 0.0 to 1.0,
                    slots object of string values, explanation string.
                    Do not execute business actions or request business data.
                    """);
            requestSpec = invoke(requestSpec, "user", "sceneId=%s\nmodel=%s\ntext=%s".formatted(sceneId, policy.model(), text));
            Object responseSpec = invoke(requestSpec, "call");
            return (LlmRecognitionResponse) invoke(responseSpec, "entity", LlmRecognitionResponse.class);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Spring AI ChatClient invocation failed", ex);
        }
    }

    private static Object buildChatClient(Object chatClientBuilder) {
        if (chatClientBuilder == null) {
            return null;
        }
        try {
            return invoke(chatClientBuilder, "build");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Spring AI ChatClient builder invocation failed", ex);
        }
    }

    private static Object invoke(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, args);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw ex;
        }
    }

    private static Method findMethod(Class<?> targetType, String methodName, Object[] args) throws NoSuchMethodException {
        for (Method method : targetType.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (args[i] != null && !parameterTypes[i].isAssignableFrom(args[i].getClass())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return method;
            }
        }
        throw new NoSuchMethodException(targetType.getName() + "#" + methodName);
    }

    private Optional<RecognitionCandidate> candidate(LlmRecognitionResponse response) {
        if (response == null || response.intentCode() == null || response.intentCode().isBlank()) {
            return Optional.empty();
        }
        if (response.confidence() < properties.minConfidence()) {
            return Optional.empty();
        }
        return Optional.of(new RecognitionCandidate(
                response.intentCode(),
                response.confidence(),
                response.slots(),
                response.explanation() == null ? "llm hit" : response.explanation()
        ));
    }

    private static final class NoopIntentMetricsPort implements IntentMetricsPort {
        @Override
        public void recordRecognition(com.intenthub.domain.recognition.Envelope envelope,
                                      com.intenthub.domain.recognition.IntentResult result,
                                      long latencyMillis) {
        }

        @Override
        public MetricsSnapshot snapshot() {
            return new MetricsSnapshot(0, 0, 0, 0, 0, 0.0, 0, 0, 0, 0.0, 0, Map.of(), Map.of(), Map.of(), Instant.EPOCH, Instant.EPOCH);
        }
    }

    private static final class NoopLlmBudgetAuditPort implements LlmBudgetAuditPort {
        @Override
        public void recordAttempt(String tenantId, String sceneId, String provider, String model, double units) {
        }

        @Override
        public boolean tryReserveDailyBudget(String tenantId, String sceneId, String provider, String model, double units, double dailyBudget) {
            return units > 0.0 && dailyBudget >= units;
        }

        @Override
        public void releaseDailyBudgetReservation(String tenantId, String sceneId, String provider, String model, double units) {
        }

        @Override
        public int reconcileStaleDailyBudgetReservations(Duration staleAfter) {
            return 0;
        }

        @Override
        public LlmBudgetUsage dailyUsage(String tenantId, String sceneId, LocalDate usageDate) {
            return new LlmBudgetUsage(tenantId, sceneId, usageDate, 0, 0.0);
        }
    }
}

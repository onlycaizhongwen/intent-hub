package com.intenthub.interfaces.security;

import com.intenthub.application.config.AuditLogPort;
import com.intenthub.application.metrics.IntentMetricsPort;
import com.intenthub.infrastructure.security.SecretRefResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminJwtProperties.class)
public class AdminSecurityConfiguration {
    @Bean
    AdminJwtVerifier adminJwtVerifier(AdminJwtProperties properties, SecretRefResolver secretRefResolver, IntentMetricsPort metricsPort) {
        return new AdminJwtVerifier(properties, secretRefResolver, new AdminJwksMetricsRecorder() {
            @Override
            public void recordFetch() {
                metricsPort.recordAdminJwksFetch();
            }

            @Override
            public void recordFetchFailure() {
                metricsPort.recordAdminJwksFetchFailure();
            }

            @Override
            public void recordCacheHit() {
                metricsPort.recordAdminJwksCacheHit();
            }

            @Override
            public void recordStaleHit() {
                metricsPort.recordAdminJwksStaleHit();
            }
        });
    }

    @Bean
    AdminJwtAuthenticationFilter adminJwtAuthenticationFilter(
            AdminJwtProperties properties,
            AdminJwtVerifier verifier,
            AuditLogPort auditLogPort,
            IntentMetricsPort metricsPort
    ) {
        return new AdminJwtAuthenticationFilter(properties, verifier, auditLogPort, metricsPort);
    }
}

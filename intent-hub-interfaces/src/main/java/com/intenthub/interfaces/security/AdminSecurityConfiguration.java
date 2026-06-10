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
    AdminJwtVerifier adminJwtVerifier(AdminJwtProperties properties, SecretRefResolver secretRefResolver) {
        return new AdminJwtVerifier(properties, secretRefResolver);
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

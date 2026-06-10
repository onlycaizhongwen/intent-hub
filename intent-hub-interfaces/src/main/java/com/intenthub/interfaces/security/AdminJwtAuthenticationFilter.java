package com.intenthub.interfaces.security;

import com.intenthub.application.config.AuditLogPort;
import com.intenthub.application.metrics.IntentMetricsPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

public class AdminJwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String ACTOR_ATTRIBUTE = AdminJwtAuthenticationFilter.class.getName() + ".actor";
    public static final String ROLES_ATTRIBUTE = AdminJwtAuthenticationFilter.class.getName() + ".roles";

    private final AdminJwtProperties properties;
    private final AdminJwtVerifier verifier;
    private final AuditLogPort auditLogPort;
    private final IntentMetricsPort metricsPort;

    public AdminJwtAuthenticationFilter(AdminJwtProperties properties, AdminJwtVerifier verifier) {
        this(properties, verifier, null, null);
    }

    public AdminJwtAuthenticationFilter(AdminJwtProperties properties, AdminJwtVerifier verifier,
                                        AuditLogPort auditLogPort, IntentMetricsPort metricsPort) {
        this.properties = properties;
        this.verifier = verifier;
        this.auditLogPort = auditLogPort;
        this.metricsPort = metricsPort;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return properties.getProtectedPathPrefixes().stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .noneMatch(prefix -> path.startsWith(prefix.trim()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            AdminJwtClaims claims = verifier.verify(bearerToken(request));
            request.setAttribute(ACTOR_ATTRIBUTE, claims.actor());
            request.setAttribute(ROLES_ATTRIBUTE, claims.roles());
            filterChain.doFilter(request, response);
        } catch (SecurityException ex) {
            recordAuthFailure(request, ex.getMessage());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"" + escape(ex.getMessage()) + "\",\"status\":403}");
        }
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new SecurityException("missing admin bearer token");
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private void recordAuthFailure(HttpServletRequest request, String reason) {
        if (auditLogPort != null) {
            auditLogPort.record(
                    parameter(request, "tenantId", "UNKNOWN"),
                    parameter(request, "sceneId", "UNKNOWN"),
                    "unknown",
                    "ADMIN_JWT_AUTH_FAILED",
                    "ADMIN_JWT",
                    request.getRequestURI(),
                    Map.of(
                            "method", request.getMethod(),
                            "path", request.getRequestURI(),
                            "reason", reason == null || reason.isBlank() ? "unknown" : reason
                    )
            );
        }
        if (metricsPort != null) {
            metricsPort.recordAdminJwtAuthFailure(reason);
        }
    }

    private String parameter(HttpServletRequest request, String name, String fallback) {
        String value = request.getParameter(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

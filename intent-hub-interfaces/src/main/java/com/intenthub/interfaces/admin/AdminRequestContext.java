package com.intenthub.interfaces.admin;

import com.intenthub.interfaces.security.AdminJwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;

final class AdminRequestContext {
    private static final String ACTOR_HEADER = "X-IntentHub-Actor";
    private static final String ROLES_HEADER = "X-IntentHub-Roles";

    private AdminRequestContext() {
    }

    static String actor(ConfigVersionActionRequest request) {
        return actor(request == null ? "system" : request.normalizedActor());
    }

    static String actor(String fallbackActor) {
        String jwtActor = attributeString(AdminJwtAuthenticationFilter.ACTOR_ATTRIBUTE);
        if (jwtActor != null && !jwtActor.isBlank()) {
            return jwtActor.trim();
        }
        String actor = header(ACTOR_HEADER);
        if (actor != null && !actor.isBlank()) {
            return actor.trim();
        }
        return fallbackActor == null || fallbackActor.isBlank() ? "system" : fallbackActor;
    }

    static List<String> roles(ConfigVersionActionRequest request) {
        List<String> roles = headerRoles();
        if (!roles.isEmpty()) {
            return roles;
        }
        return request == null ? List.of() : request.normalizedRoles();
    }

    static List<String> roles(List<String> fallbackRoles) {
        List<String> roles = headerRoles();
        if (!roles.isEmpty()) {
            return roles;
        }
        return fallbackRoles == null ? List.of() : fallbackRoles;
    }

    private static List<String> headerRoles() {
        List<String> jwtRoles = attributeRoles(AdminJwtAuthenticationFilter.ROLES_ATTRIBUTE);
        if (!jwtRoles.isEmpty()) {
            return jwtRoles;
        }
        String roles = header(ROLES_HEADER);
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
    }

    private static String attributeString(String name) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(name);
        return value instanceof String text ? text : null;
    }

    private static List<String> attributeRoles(String name) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return List.of();
        }
        Object value = request.getAttribute(name);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
    }

    private static String header(String name) {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader(name);
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest();
    }
}

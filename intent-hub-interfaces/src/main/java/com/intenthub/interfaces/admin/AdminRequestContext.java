package com.intenthub.interfaces.admin;

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
        String actor = header(ACTOR_HEADER);
        if (actor != null && !actor.isBlank()) {
            return actor.trim();
        }
        return request == null ? "system" : request.normalizedActor();
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

    private static String header(String name) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request == null ? null : request.getHeader(name);
    }
}

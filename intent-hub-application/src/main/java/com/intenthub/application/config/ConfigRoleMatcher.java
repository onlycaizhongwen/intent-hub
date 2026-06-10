package com.intenthub.application.config;

import java.util.List;

final class ConfigRoleMatcher {
    private ConfigRoleMatcher() {
    }

    static boolean hasRole(List<String> roles, String requiredRole, String tenantId, String sceneId) {
        if (roles == null) {
            return true;
        }
        return roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .anyMatch(role -> matches(role, requiredRole, tenantId, sceneId));
    }

    static boolean hasAnyRole(List<String> roles, List<String> requiredRoles, String tenantId, String sceneId) {
        if (roles == null) {
            return true;
        }
        return requiredRoles.stream()
                .anyMatch(requiredRole -> hasRole(roles, requiredRole, tenantId, sceneId));
    }

    static String requiredRoleHint(String requiredRole, String tenantId, String sceneId) {
        return requiredRole + " or " + requiredRole + ":" + tenantId + ":" + sceneId;
    }

    private static boolean matches(String role, String requiredRole, String tenantId, String sceneId) {
        if (requiredRole.equals(role)) {
            return true;
        }
        String[] parts = role.split(":", -1);
        if (parts.length != 3 || !requiredRole.equals(parts[0])) {
            return false;
        }
        return matchesScope(parts[1], tenantId) && matchesScope(parts[2], sceneId);
    }

    private static boolean matchesScope(String actual, String expected) {
        return "*".equals(actual) || expected.equals(actual);
    }
}

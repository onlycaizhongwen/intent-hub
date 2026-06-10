package com.intenthub.application.config;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ConfigPermission {
    static final String VIEWER = "CONFIG_VIEWER";
    static final String EDITOR = "CONFIG_EDITOR";
    static final String APPROVER = "CONFIG_APPROVER";
    static final String PUBLISHER = "CONFIG_PUBLISHER";

    private ConfigPermission() {
    }

    static void requireViewer(List<String> roles, String tenantId, String sceneId, String action) {
        requireViewer(roles, tenantId, sceneId, action, null);
    }

    static void requireViewer(List<String> roles, String tenantId, String sceneId, String action, AuditLogPort auditLogPort) {
        if (!canView(roles, tenantId, sceneId)) {
            deny(roles, VIEWER, tenantId, sceneId, action, auditLogPort);
        }
    }

    static void requireEditor(List<String> roles, String tenantId, String sceneId, String action) {
        requireEditor(roles, tenantId, sceneId, action, null);
    }

    static void requireEditor(List<String> roles, String tenantId, String sceneId, String action, AuditLogPort auditLogPort) {
        requireRole(roles, EDITOR, tenantId, sceneId, action, auditLogPort);
    }

    static void requireApprover(List<String> roles, String tenantId, String sceneId, String action) {
        requireApprover(roles, tenantId, sceneId, action, null);
    }

    static void requireApprover(List<String> roles, String tenantId, String sceneId, String action, AuditLogPort auditLogPort) {
        requireRole(roles, APPROVER, tenantId, sceneId, action, auditLogPort);
    }

    static void requirePublisher(List<String> roles, String tenantId, String sceneId, String action) {
        requirePublisher(roles, tenantId, sceneId, action, null);
    }

    static void requirePublisher(List<String> roles, String tenantId, String sceneId, String action, AuditLogPort auditLogPort) {
        requireRole(roles, PUBLISHER, tenantId, sceneId, action, auditLogPort);
    }

    static boolean canView(List<String> roles, String tenantId, String sceneId) {
        return ConfigRoleMatcher.hasAnyRole(roles, List.of(VIEWER, EDITOR, APPROVER, PUBLISHER), tenantId, sceneId);
    }

    static String viewerRoleHint(String tenantId, String sceneId) {
        return ConfigRoleMatcher.requiredRoleHint(VIEWER, tenantId, sceneId);
    }

    private static void requireRole(List<String> roles, String requiredRole, String tenantId, String sceneId, String action) {
        if (!ConfigRoleMatcher.hasRole(roles, requiredRole, tenantId, sceneId)) {
            deny(roles, requiredRole, tenantId, sceneId, action, null);
        }
    }

    private static void requireRole(List<String> roles, String requiredRole, String tenantId, String sceneId, String action, AuditLogPort auditLogPort) {
        if (!ConfigRoleMatcher.hasRole(roles, requiredRole, tenantId, sceneId)) {
            deny(roles, requiredRole, tenantId, sceneId, action, auditLogPort);
        }
    }

    private static void deny(List<String> roles, String requiredRole, String tenantId, String sceneId, String action, AuditLogPort auditLogPort) {
        String roleHint = ConfigRoleMatcher.requiredRoleHint(requiredRole, tenantId, sceneId);
        if (auditLogPort != null) {
            auditLogPort.record(tenantId, sceneId, "unknown", "CONFIG_PERMISSION_DENIED", "CONFIG_PERMISSION", sceneId, Map.of(
                    "action", action,
                    "requiredRole", requiredRole,
                    "roleHint", roleHint,
                    "roles", normalizedRoles(roles)
            ));
        }
        throw new SecurityException(action + " requires role " + roleHint);
    }

    private static String normalizedRoles(List<String> roles) {
        if (roles == null) {
            return "__internal__";
        }
        if (roles.isEmpty()) {
            return "__empty__";
        }
        return roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
    }
}

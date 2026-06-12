package com.intenthub.application.config;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

final class ConfigPermission {
    static final String VIEWER = "CONFIG_VIEWER";
    static final String EDITOR = "CONFIG_EDITOR";
    static final String INTENT_EDITOR = "CONFIG_INTENT_EDITOR";
    static final String SLOT_EDITOR = "CONFIG_SLOT_EDITOR";
    static final String SYNONYM_EDITOR = "CONFIG_SYNONYM_EDITOR";
    static final String STRATEGY_EDITOR = "CONFIG_STRATEGY_EDITOR";
    static final String ROUTE_EDITOR = "CONFIG_ROUTE_EDITOR";
    static final String ACTION_EDITOR = "CONFIG_ACTION_EDITOR";
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

    static void requireObjectEditor(List<String> roles, String tenantId, String sceneId, ConfigObjectType type, String action) {
        requireObjectEditor(roles, tenantId, sceneId, type, action, null);
    }

    static void requireObjectEditor(List<String> roles, String tenantId, String sceneId, ConfigObjectType type, String action, AuditLogPort auditLogPort) {
        String objectRole = objectEditorRole(type);
        if (!ConfigRoleMatcher.hasAnyRole(roles, List.of(EDITOR, objectRole), tenantId, sceneId)) {
            deny(roles, EDITOR, tenantId, sceneId, action, auditLogPort, objectRole, Map.of(
                    "objectType", type.name()
            ));
        }
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

    static String objectEditorRole(ConfigObjectType type) {
        return switch (type) {
            case INTENT -> INTENT_EDITOR;
            case SLOT -> SLOT_EDITOR;
            case SYNONYM -> SYNONYM_EDITOR;
            case STRATEGY -> STRATEGY_EDITOR;
            case ROUTE -> ROUTE_EDITOR;
            case DOWNSTREAM_ACTION -> ACTION_EDITOR;
        };
    }

    private static void deny(List<String> roles, String requiredRole, String tenantId, String sceneId, String action, AuditLogPort auditLogPort) {
        deny(roles, requiredRole, tenantId, sceneId, action, auditLogPort, null, Map.of());
    }

    private static void deny(List<String> roles, String requiredRole, String tenantId, String sceneId, String action, AuditLogPort auditLogPort, String alternativeRole, Map<String, String> extraDetail) {
        String roleHint = ConfigRoleMatcher.requiredRoleHint(requiredRole, tenantId, sceneId);
        if (alternativeRole != null && !alternativeRole.isBlank()) {
            roleHint = roleHint + " or " + ConfigRoleMatcher.requiredRoleHint(alternativeRole, tenantId, sceneId);
        }
        if (auditLogPort != null) {
            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("action", action);
            detail.put("requiredRole", requiredRole);
            if (alternativeRole != null && !alternativeRole.isBlank()) {
                detail.put("alternativeRole", alternativeRole);
            }
            detail.put("roleHint", roleHint);
            detail.put("roles", normalizedRoles(roles));
            detail.putAll(extraDetail);
            auditLogPort.record(tenantId, sceneId, "unknown", "CONFIG_PERMISSION_DENIED", "CONFIG_PERMISSION", sceneId, detail);
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

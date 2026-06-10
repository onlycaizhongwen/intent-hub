package com.intenthub.interfaces.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "intent-hub.admin.jwt")
public class AdminJwtProperties {
    private boolean enabled = false;
    private String secret;
    private String secretRef;
    private String actorClaim = "sub";
    private String rolesClaim = "roles";
    private String issuer;
    private String audience;
    private List<String> protectedPathPrefixes = new ArrayList<>(List.of("/api/v1/admin/config"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public void setSecretRef(String secretRef) {
        this.secretRef = secretRef;
    }

    public String getActorClaim() {
        return actorClaim;
    }

    public void setActorClaim(String actorClaim) {
        this.actorClaim = actorClaim;
    }

    public String getRolesClaim() {
        return rolesClaim;
    }

    public void setRolesClaim(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public List<String> getProtectedPathPrefixes() {
        return protectedPathPrefixes;
    }

    public void setProtectedPathPrefixes(List<String> protectedPathPrefixes) {
        this.protectedPathPrefixes = protectedPathPrefixes == null ? List.of() : protectedPathPrefixes;
    }
}

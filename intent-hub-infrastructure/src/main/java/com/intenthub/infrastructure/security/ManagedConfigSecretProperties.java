package com.intenthub.infrastructure.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "intent-hub.secret.managed-config")
public class ManagedConfigSecretProperties {
    private boolean enabled;
    private Map<String, String> refs = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getRefs() {
        return Collections.unmodifiableMap(refs);
    }

    public void setRefs(Map<String, String> refs) {
        this.refs = refs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(refs);
    }
}

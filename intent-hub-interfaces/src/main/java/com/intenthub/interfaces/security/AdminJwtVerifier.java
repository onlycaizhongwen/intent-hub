package com.intenthub.interfaces.security;

import com.intenthub.infrastructure.security.SecretRefResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class AdminJwtVerifier {
    private final AdminJwtProperties properties;
    private final SecretRefResolver secretRefResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminJwtVerifier(AdminJwtProperties properties, SecretRefResolver secretRefResolver) {
        this.properties = properties;
        this.secretRefResolver = secretRefResolver;
    }

    public AdminJwtClaims verify(String token) {
        if (token == null || token.isBlank()) {
            throw new SecurityException("missing admin bearer token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new SecurityException("invalid admin bearer token");
        }
        JsonNode header = json(parts[0]);
        if (!"HS256".equals(text(header, "alg"))) {
            throw new SecurityException("unsupported admin jwt algorithm");
        }
        verifySignature(parts);
        JsonNode payload = json(parts[1]);
        verifyRegisteredClaims(payload);
        String actor = text(payload, normalizedClaim(properties.getActorClaim(), "sub"));
        List<String> roles = roles(payload.get(normalizedClaim(properties.getRolesClaim(), "roles")));
        if (actor == null || actor.isBlank()) {
            throw new SecurityException("admin jwt actor claim is required");
        }
        if (roles.isEmpty()) {
            throw new SecurityException("admin jwt roles claim is required");
        }
        return new AdminJwtClaims(actor.trim(), roles);
    }

    private void verifySignature(String[] parts) {
        byte[] expected = hmacSha256(parts[0] + "." + parts[1], secret());
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            throw new SecurityException("invalid admin jwt signature");
        }
        if (!java.security.MessageDigest.isEqual(expected, actual)) {
            throw new SecurityException("invalid admin jwt signature");
        }
    }

    private void verifyRegisteredClaims(JsonNode payload) {
        JsonNode exp = payload.get("exp");
        if (exp != null && exp.isNumber() && exp.asLong() < Instant.now().getEpochSecond()) {
            throw new SecurityException("admin jwt is expired");
        }
        String issuer = blankToNull(properties.getIssuer());
        if (issuer != null && !issuer.equals(text(payload, "iss"))) {
            throw new SecurityException("admin jwt issuer is invalid");
        }
        String audience = blankToNull(properties.getAudience());
        if (audience != null && !audienceMatches(payload.get("aud"), audience)) {
            throw new SecurityException("admin jwt audience is invalid");
        }
    }

    private boolean audienceMatches(JsonNode aud, String expected) {
        if (aud == null) {
            return false;
        }
        if (aud.isTextual()) {
            return expected.equals(aud.asText());
        }
        if (aud.isArray()) {
            for (JsonNode item : aud) {
                if (item.isTextual() && expected.equals(item.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> roles(JsonNode node) {
        if (node == null) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    roles.add(item.asText());
                }
            }
        } else if (node.isTextual()) {
            roles.addAll(Arrays.asList(node.asText().split(",")));
        }
        return roles.stream()
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
    }

    private byte[] hmacSha256(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new SecurityException("admin jwt signature verification failed");
        }
    }

    private JsonNode json(String base64Url) {
        try {
            return objectMapper.readTree(Base64.getUrlDecoder().decode(base64Url));
        } catch (Exception ex) {
            throw new SecurityException("invalid admin jwt payload");
        }
    }

    private String secret() {
        String directSecret = blankToNull(properties.getSecret());
        if (directSecret != null) {
            return directSecret;
        }
        String secretRef = blankToNull(properties.getSecretRef());
        if (secretRef != null) {
            return secretRefResolver.resolve(secretRef)
                    .filter(value -> !value.isBlank())
                    .orElseThrow(() -> new SecurityException("admin jwt secret ref is unavailable"));
        }
        throw new SecurityException("admin jwt secret is not configured");
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    private String normalizedClaim(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

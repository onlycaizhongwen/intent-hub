package com.intenthub.interfaces.security;

import com.intenthub.infrastructure.security.SecretRefResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class AdminJwtVerifier {
    private final AdminJwtProperties properties;
    private final SecretRefResolver secretRefResolver;
    private final AdminJwksMetricsRecorder jwksMetricsRecorder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private String cachedJwksJson;
    private Instant cachedJwksExpiresAt = Instant.EPOCH;
    private Instant cachedJwksStaleUntil = Instant.EPOCH;
    private String cachedDiscoveryJwksUrl;

    public AdminJwtVerifier(AdminJwtProperties properties, SecretRefResolver secretRefResolver) {
        this(properties, secretRefResolver, AdminJwksMetricsRecorder.NOOP);
    }

    public AdminJwtVerifier(AdminJwtProperties properties, SecretRefResolver secretRefResolver,
                            AdminJwksMetricsRecorder jwksMetricsRecorder) {
        this.properties = properties;
        this.secretRefResolver = secretRefResolver;
        this.jwksMetricsRecorder = jwksMetricsRecorder == null ? AdminJwksMetricsRecorder.NOOP : jwksMetricsRecorder;
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
        verifySignature(parts, header);
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

    private void verifySignature(String[] parts, JsonNode header) {
        String algorithm = text(header, "alg");
        if ("HS256".equals(algorithm)) {
            verifyHs256Signature(parts);
            return;
        }
        if ("RS256".equals(algorithm)) {
            verifyRs256Signature(parts, blankToNull(text(header, "kid")));
            return;
        }
        throw new SecurityException("unsupported admin jwt algorithm");
    }

    private void verifyHs256Signature(String[] parts) {
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

    private void verifyRs256Signature(String[] parts, String kid) {
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            throw new SecurityException("invalid admin jwt signature");
        }
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(rsaPublicKey(kid));
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
            if (!signature.verify(signatureBytes)) {
                throw new SecurityException("invalid admin jwt signature");
            }
        } catch (SecurityException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SecurityException("admin jwt signature verification failed");
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

    private PublicKey rsaPublicKey(String kid) {
        JsonNode keys = jwks().get("keys");
        if (keys == null || !keys.isArray() || keys.size() == 0) {
            throw new SecurityException("admin jwt jwks has no keys");
        }
        JsonNode selected = null;
        int rsaKeyCount = 0;
        for (JsonNode key : keys) {
            if (!"RSA".equals(text(key, "kty"))) {
                continue;
            }
            rsaKeyCount++;
            if (kid != null && kid.equals(text(key, "kid"))) {
                selected = key;
                break;
            }
            if (kid == null && selected == null) {
                selected = key;
            }
        }
        if (kid == null && rsaKeyCount > 1) {
            throw new SecurityException("admin jwt key id is required");
        }
        if (selected == null) {
            throw new SecurityException("admin jwt key is unavailable");
        }
        try {
            BigInteger modulus = unsignedBase64UrlInteger(text(selected, "n"));
            BigInteger exponent = unsignedBase64UrlInteger(text(selected, "e"));
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (SecurityException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SecurityException("admin jwt key is invalid");
        }
    }

    private JsonNode jwks() {
        String inline = blankToNull(properties.getJwksJson());
        if (inline != null) {
            return parseJwks(inline);
        }
        String url = blankToNull(properties.getJwksUrl());
        if (url != null) {
            return parseJwks(fetchJwks(url));
        }
        String discoveryUrl = blankToNull(properties.getOidcDiscoveryUrl());
        if (discoveryUrl != null) {
            return parseJwks(fetchJwks(resolveDiscoveryJwksUrl(discoveryUrl)));
        }
        throw new SecurityException("admin jwt jwks is not configured");
    }

    private JsonNode parseJwks(String jwksJson) {
        try {
            return objectMapper.readTree(jwksJson);
        } catch (Exception ex) {
            throw new SecurityException("admin jwt jwks is invalid");
        }
    }

    private synchronized String fetchJwks(String url) {
        Instant now = Instant.now();
        if (cachedJwksJson != null && now.isBefore(cachedJwksExpiresAt)) {
            jwksMetricsRecorder.recordCacheHit();
            return cachedJwksJson;
        }
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url)).GET();
            if (jwksFetchTimeout() != null) {
                requestBuilder.timeout(jwksFetchTimeout());
            }
            jwksMetricsRecorder.recordFetch();
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SecurityException("admin jwt jwks fetch failed");
            }
            parseJwks(response.body());
            cachedJwksJson = response.body();
            Instant fetchedAt = Instant.now();
            cachedJwksExpiresAt = fetchedAt.plusSeconds(jwksCacheTtlSeconds());
            cachedJwksStaleUntil = cachedJwksExpiresAt.plusSeconds(jwksStaleGraceSeconds());
            return cachedJwksJson;
        } catch (SecurityException ex) {
            jwksMetricsRecorder.recordFetchFailure();
            if (cachedJwksJson != null && Instant.now().isBefore(cachedJwksStaleUntil)) {
                jwksMetricsRecorder.recordStaleHit();
                return cachedJwksJson;
            }
            throw ex;
        } catch (Exception ex) {
            jwksMetricsRecorder.recordFetchFailure();
            if (cachedJwksJson != null && Instant.now().isBefore(cachedJwksStaleUntil)) {
                jwksMetricsRecorder.recordStaleHit();
                return cachedJwksJson;
            }
            throw new SecurityException("admin jwt jwks fetch failed");
        }
    }

    private synchronized String resolveDiscoveryJwksUrl(String discoveryUrl) {
        if (cachedDiscoveryJwksUrl != null) {
            return cachedDiscoveryJwksUrl;
        }
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(discoveryUrl)).GET();
            if (jwksFetchTimeout() != null) {
                requestBuilder.timeout(jwksFetchTimeout());
            }
            jwksMetricsRecorder.recordDiscoveryFetch();
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SecurityException("admin jwt oidc discovery fetch failed");
            }
            JsonNode discovery = objectMapper.readTree(response.body());
            verifyDiscoveryIssuer(discovery);
            String jwksUri = blankToNull(text(discovery, "jwks_uri"));
            if (jwksUri == null) {
                throw new SecurityException("admin jwt oidc discovery jwks_uri is unavailable");
            }
            cachedDiscoveryJwksUrl = jwksUri;
            return cachedDiscoveryJwksUrl;
        } catch (SecurityException ex) {
            jwksMetricsRecorder.recordDiscoveryFetchFailure();
            throw ex;
        } catch (Exception ex) {
            jwksMetricsRecorder.recordDiscoveryFetchFailure();
            throw new SecurityException("admin jwt oidc discovery fetch failed");
        }
    }

    private void verifyDiscoveryIssuer(JsonNode discovery) {
        if (!properties.isOidcDiscoveryIssuerValidationEnabled()) {
            return;
        }
        String expectedIssuer = blankToNull(properties.getIssuer());
        if (expectedIssuer == null) {
            return;
        }
        String discoveryIssuer = blankToNull(text(discovery, "issuer"));
        if (!expectedIssuer.equals(discoveryIssuer)) {
            throw new SecurityException("admin jwt oidc discovery issuer is invalid");
        }
    }

    private long jwksCacheTtlSeconds() {
        return Math.max(properties.getJwksCacheTtlSeconds(), 0);
    }

    private long jwksStaleGraceSeconds() {
        return Math.max(properties.getJwksStaleGraceSeconds(), 0);
    }

    private Duration jwksFetchTimeout() {
        long timeoutMs = properties.getJwksFetchTimeoutMs();
        return timeoutMs <= 0 ? null : Duration.ofMillis(timeoutMs);
    }

    private BigInteger unsignedBase64UrlInteger(String value) {
        if (value == null || value.isBlank()) {
            throw new SecurityException("admin jwt key is invalid");
        }
        try {
            return new BigInteger(1, Base64.getUrlDecoder().decode(value));
        } catch (IllegalArgumentException ex) {
            throw new SecurityException("admin jwt key is invalid");
        }
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

package com.intenthub.interfaces.security;

import com.intenthub.infrastructure.security.SecretRefResolver;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminJwtVerifierTest {
    @Test
    void verifiesHs256TokenAndExtractsActorAndRoles() {
        AdminJwtProperties properties = new AdminJwtProperties();
        properties.setSecret("jwt-secret");
        AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver());

        AdminJwtClaims claims = verifier.verify(token("jwt-secret", """
                {"sub":"iam-admin","roles":["CONFIG_VIEWER","CONFIG_APPROVER"],"exp":%d}
                """.formatted(Instant.now().plusSeconds(60).getEpochSecond())));

        assertThat(claims.actor()).isEqualTo("iam-admin");
        assertThat(claims.roles()).containsExactly("CONFIG_VIEWER", "CONFIG_APPROVER");
    }

    @Test
    void supportsSecretRefIssuerAudienceAndCommaSeparatedRoles() {
        AdminJwtProperties properties = new AdminJwtProperties();
        properties.setSecretRef("ADMIN_JWT_SECRET");
        properties.setIssuer("intent-hub");
        properties.setAudience("admin-portal");
        AdminJwtVerifier verifier = new AdminJwtVerifier(properties, ref -> "ADMIN_JWT_SECRET".equals(ref) ? Optional.of("ref-secret") : Optional.empty());

        AdminJwtClaims claims = verifier.verify(token("ref-secret", """
                {"sub":"scene-admin","roles":"CONFIG_VIEWER:demo:order-scene,CONFIG_EDITOR:demo:*","iss":"intent-hub","aud":["admin-portal"],"exp":%d}
                """.formatted(Instant.now().plusSeconds(60).getEpochSecond())));

        assertThat(claims.actor()).isEqualTo("scene-admin");
        assertThat(claims.roles()).containsExactly("CONFIG_VIEWER:demo:order-scene", "CONFIG_EDITOR:demo:*");
    }

    @Test
    void verifiesRs256TokenWithJwks() {
        KeyPair keyPair = rsaKeyPair();
        AdminJwtProperties properties = new AdminJwtProperties();
        properties.setJwksJson(jwks("iam-key-1", (RSAPublicKey) keyPair.getPublic()));
        properties.setIssuer("https://iam.example.com");
        properties.setAudience("intent-hub-admin");
        AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver());

        AdminJwtClaims claims = verifier.verify(rs256Token("iam-key-1", keyPair, """
                {"sub":"oidc-admin","roles":["CONFIG_VIEWER:demo:order-scene"],"iss":"https://iam.example.com","aud":"intent-hub-admin","exp":%d}
                """.formatted(Instant.now().plusSeconds(60).getEpochSecond())));

        assertThat(claims.actor()).isEqualTo("oidc-admin");
        assertThat(claims.roles()).containsExactly("CONFIG_VIEWER:demo:order-scene");
    }

    @Test
    void verifiesRs256TokenWithJwksUrlAndCachesResponse() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        AtomicInteger jwksRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/jwks.json", exchange -> {
            jwksRequests.incrementAndGet();
            byte[] body = jwks("iam-key-url", (RSAPublicKey) keyPair.getPublic()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AdminJwtProperties properties = new AdminJwtProperties();
            properties.setJwksUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json");
            RecordingJwksMetricsRecorder metrics = new RecordingJwksMetricsRecorder();
            AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver(), metrics);
            String token = rs256Token("iam-key-url", keyPair, """
                    {"sub":"jwks-url-admin","roles":["CONFIG_VIEWER"],"exp":%d}
                    """.formatted(Instant.now().plusSeconds(60).getEpochSecond()));

            AdminJwtClaims firstClaims = verifier.verify(token);
            AdminJwtClaims secondClaims = verifier.verify(token);

            assertThat(firstClaims.actor()).isEqualTo("jwks-url-admin");
            assertThat(secondClaims.roles()).containsExactly("CONFIG_VIEWER");
            assertThat(jwksRequests).hasValue(1);
            assertThat(metrics.fetches).hasValue(1);
            assertThat(metrics.cacheHits).hasValue(1);
            assertThat(metrics.fetchFailures).hasValue(0);
            assertThat(metrics.staleHits).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void verifiesRs256TokenWithOidcDiscoveryJwksUri() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        AtomicInteger discoveryRequests = new AtomicInteger();
        AtomicInteger jwksRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/openid-configuration", exchange -> {
            discoveryRequests.incrementAndGet();
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            byte[] body = ("""
                    {"issuer":"%s","jwks_uri":"%s/.well-known/jwks.json"}
                    """.formatted(baseUrl, baseUrl)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/.well-known/jwks.json", exchange -> {
            jwksRequests.incrementAndGet();
            byte[] body = jwks("iam-key-discovery", (RSAPublicKey) keyPair.getPublic()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String issuer = "http://127.0.0.1:" + server.getAddress().getPort();
            AdminJwtProperties properties = new AdminJwtProperties();
            properties.setOidcDiscoveryUrl(issuer + "/.well-known/openid-configuration");
            properties.setIssuer(issuer);
            RecordingJwksMetricsRecorder metrics = new RecordingJwksMetricsRecorder();
            AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver(), metrics);
            String token = rs256Token("iam-key-discovery", keyPair, """
                    {"sub":"oidc-discovery-admin","roles":["CONFIG_VIEWER"],"iss":"%s","exp":%d}
                    """.formatted(issuer, Instant.now().plusSeconds(60).getEpochSecond()));

            AdminJwtClaims claims = verifier.verify(token);

            assertThat(claims.actor()).isEqualTo("oidc-discovery-admin");
            assertThat(claims.roles()).containsExactly("CONFIG_VIEWER");
            assertThat(discoveryRequests).hasValue(1);
            assertThat(jwksRequests).hasValue(1);
            assertThat(metrics.discoveryFetches).hasValue(1);
            assertThat(metrics.discoveryFetchFailures).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsOidcDiscoveryWithoutJwksUri() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/openid-configuration", exchange -> {
            byte[] body = "{\"issuer\":\"local-issuer\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AdminJwtProperties properties = new AdminJwtProperties();
            properties.setOidcDiscoveryUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/openid-configuration");
            RecordingJwksMetricsRecorder metrics = new RecordingJwksMetricsRecorder();
            AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver(), metrics);
            String token = rs256Token("missing-discovery-jwks", keyPair, """
                    {"sub":"oidc-discovery-admin","roles":["CONFIG_VIEWER"],"exp":%d}
                    """.formatted(Instant.now().plusSeconds(60).getEpochSecond()));

            assertThatThrownBy(() -> verifier.verify(token))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("admin jwt oidc discovery jwks_uri is unavailable");
            assertThat(metrics.discoveryFetches).hasValue(1);
            assertThat(metrics.discoveryFetchFailures).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsOidcDiscoveryWhenIssuerDoesNotMatchConfiguredIssuer() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/openid-configuration", exchange -> {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            byte[] body = ("""
                    {"issuer":"https://wrong-issuer.example.com","jwks_uri":"%s/.well-known/jwks.json"}
                    """.formatted(baseUrl)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AdminJwtProperties properties = new AdminJwtProperties();
            properties.setIssuer("https://iam.example.com");
            properties.setOidcDiscoveryUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/openid-configuration");
            RecordingJwksMetricsRecorder metrics = new RecordingJwksMetricsRecorder();
            AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver(), metrics);
            String token = rs256Token("issuer-mismatch-discovery", keyPair, """
                    {"sub":"oidc-discovery-admin","roles":["CONFIG_VIEWER"],"iss":"https://iam.example.com","exp":%d}
                    """.formatted(Instant.now().plusSeconds(60).getEpochSecond()));

            assertThatThrownBy(() -> verifier.verify(token))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("admin jwt oidc discovery issuer is invalid");
            assertThat(metrics.discoveryFetches).hasValue(1);
            assertThat(metrics.discoveryFetchFailures).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void allowsOidcDiscoveryIssuerMismatchWhenValidationIsDisabled() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/openid-configuration", exchange -> {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            byte[] body = ("""
                    {"issuer":"https://wrong-issuer.example.com","jwks_uri":"%s/.well-known/jwks.json"}
                    """.formatted(baseUrl)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/.well-known/jwks.json", exchange -> {
            byte[] body = jwks("issuer-validation-disabled", (RSAPublicKey) keyPair.getPublic()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AdminJwtProperties properties = new AdminJwtProperties();
            properties.setIssuer("https://iam.example.com");
            properties.setOidcDiscoveryUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/openid-configuration");
            properties.setOidcDiscoveryIssuerValidationEnabled(false);
            AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver());
            String token = rs256Token("issuer-validation-disabled", keyPair, """
                    {"sub":"oidc-discovery-admin","roles":["CONFIG_VIEWER"],"iss":"https://iam.example.com","exp":%d}
                    """.formatted(Instant.now().plusSeconds(60).getEpochSecond()));

            AdminJwtClaims claims = verifier.verify(token);

            assertThat(claims.actor()).isEqualTo("oidc-discovery-admin");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refreshesJwksUrlWhenCacheTtlExpires() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        AtomicInteger jwksRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/jwks.json", exchange -> {
            jwksRequests.incrementAndGet();
            byte[] body = jwks("iam-key-refresh", (RSAPublicKey) keyPair.getPublic()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AdminJwtProperties properties = new AdminJwtProperties();
            properties.setJwksUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json");
            properties.setJwksCacheTtlSeconds(0);
            AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver());
            String token = rs256Token("iam-key-refresh", keyPair, """
                    {"sub":"jwks-refresh-admin","roles":["CONFIG_VIEWER"],"exp":%d}
                    """.formatted(Instant.now().plusSeconds(60).getEpochSecond()));

            verifier.verify(token);
            verifier.verify(token);

            assertThat(jwksRequests).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reusesStaleJwksWhenRefreshFailsWithinGraceWindow() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        AtomicInteger jwksRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/jwks.json", exchange -> {
            int request = jwksRequests.incrementAndGet();
            if (request == 1) {
                byte[] body = jwks("iam-key-stale", (RSAPublicKey) keyPair.getPublic()).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.sendResponseHeaders(500, -1);
            }
            exchange.close();
        });
        server.start();
        try {
            AdminJwtProperties properties = new AdminJwtProperties();
            properties.setJwksUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json");
            properties.setJwksCacheTtlSeconds(0);
            properties.setJwksStaleGraceSeconds(60);
            RecordingJwksMetricsRecorder metrics = new RecordingJwksMetricsRecorder();
            AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver(), metrics);
            String token = rs256Token("iam-key-stale", keyPair, """
                    {"sub":"jwks-stale-admin","roles":["CONFIG_VIEWER"],"exp":%d}
                    """.formatted(Instant.now().plusSeconds(60).getEpochSecond()));

            AdminJwtClaims firstClaims = verifier.verify(token);
            AdminJwtClaims staleClaims = verifier.verify(token);

            assertThat(firstClaims.actor()).isEqualTo("jwks-stale-admin");
            assertThat(staleClaims.roles()).containsExactly("CONFIG_VIEWER");
            assertThat(jwksRequests).hasValue(2);
            assertThat(metrics.fetches).hasValue(2);
            assertThat(metrics.fetchFailures).hasValue(1);
            assertThat(metrics.staleHits).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsJwksUrlWhenRefreshFailsAfterGraceWindow() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        AtomicInteger jwksRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/jwks.json", exchange -> {
            int request = jwksRequests.incrementAndGet();
            if (request == 1) {
                byte[] body = jwks("iam-key-stale-expired", (RSAPublicKey) keyPair.getPublic()).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.sendResponseHeaders(500, -1);
            }
            exchange.close();
        });
        server.start();
        try {
            AdminJwtProperties properties = new AdminJwtProperties();
            properties.setJwksUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json");
            properties.setJwksCacheTtlSeconds(0);
            properties.setJwksStaleGraceSeconds(0);
            AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver());
            String token = rs256Token("iam-key-stale-expired", keyPair, """
                    {"sub":"jwks-expired-admin","roles":["CONFIG_VIEWER"],"exp":%d}
                    """.formatted(Instant.now().plusSeconds(60).getEpochSecond()));

            verifier.verify(token);

            assertThatThrownBy(() -> verifier.verify(token))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("admin jwt jwks fetch failed");
            assertThat(jwksRequests).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsJwksUrlWhenFetchTimesOut() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        AtomicInteger jwksRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/jwks.json", exchange -> {
            jwksRequests.incrementAndGet();
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            byte[] body = jwks("iam-key-timeout", (RSAPublicKey) keyPair.getPublic()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AdminJwtProperties properties = new AdminJwtProperties();
            properties.setJwksUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json");
            properties.setJwksFetchTimeoutMs(50);
            RecordingJwksMetricsRecorder metrics = new RecordingJwksMetricsRecorder();
            AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver(), metrics);
            String token = rs256Token("iam-key-timeout", keyPair, """
                    {"sub":"jwks-timeout-admin","roles":["CONFIG_VIEWER"],"exp":%d}
                    """.formatted(Instant.now().plusSeconds(60).getEpochSecond()));

            assertThatThrownBy(() -> verifier.verify(token))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("admin jwt jwks fetch failed");
            assertThat(jwksRequests.get()).isBetween(0, 1);
            assertThat(metrics.fetches).hasValue(1);
            assertThat(metrics.fetchFailures).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsRs256TokenWhenJwksKeyDoesNotMatch() {
        KeyPair signingKey = rsaKeyPair();
        KeyPair otherKey = rsaKeyPair();
        AdminJwtProperties properties = new AdminJwtProperties();
        properties.setJwksJson(jwks("iam-key-1", (RSAPublicKey) otherKey.getPublic()));
        AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver());

        String token = rs256Token("iam-key-1", signingKey, """
                {"sub":"oidc-admin","roles":["CONFIG_VIEWER"],"exp":%d}
                """.formatted(Instant.now().plusSeconds(60).getEpochSecond()));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("invalid admin jwt signature");
    }

    @Test
    void rejectsRs256TokenWithoutKidWhenJwksHasMultipleKeys() {
        KeyPair signingKey = rsaKeyPair();
        KeyPair otherKey = rsaKeyPair();
        AdminJwtProperties properties = new AdminJwtProperties();
        properties.setJwksJson("""
                {"keys":[%s,%s]}
                """.formatted(jwk("iam-key-1", (RSAPublicKey) signingKey.getPublic()), jwk("iam-key-2", (RSAPublicKey) otherKey.getPublic())));
        AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver());

        String token = rs256Token(null, signingKey, """
                {"sub":"oidc-admin","roles":["CONFIG_VIEWER"],"exp":%d}
                """.formatted(Instant.now().plusSeconds(60).getEpochSecond()));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("admin jwt key id is required");
    }

    @Test
    void rejectsInvalidSignature() {
        AdminJwtProperties properties = new AdminJwtProperties();
        properties.setSecret("jwt-secret");
        AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver());

        String signedWithOtherSecret = token("other-secret", """
                {"sub":"iam-admin","roles":["CONFIG_VIEWER"],"exp":%d}
                """.formatted(Instant.now().plusSeconds(60).getEpochSecond()));

        assertThatThrownBy(() -> verifier.verify(signedWithOtherSecret))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("invalid admin jwt signature");
    }

    @Test
    void rejectsExpiredToken() {
        AdminJwtProperties properties = new AdminJwtProperties();
        properties.setSecret("jwt-secret");
        AdminJwtVerifier verifier = new AdminJwtVerifier(properties, unusedResolver());

        String expired = token("jwt-secret", """
                {"sub":"iam-admin","roles":["CONFIG_VIEWER"],"exp":%d}
                """.formatted(Instant.now().minusSeconds(60).getEpochSecond()));

        assertThatThrownBy(() -> verifier.verify(expired))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("admin jwt is expired");
    }

    private static SecretRefResolver unusedResolver() {
        return ref -> Optional.empty();
    }

    private static final class RecordingJwksMetricsRecorder implements AdminJwksMetricsRecorder {
        private final AtomicInteger fetches = new AtomicInteger();
        private final AtomicInteger fetchFailures = new AtomicInteger();
        private final AtomicInteger cacheHits = new AtomicInteger();
        private final AtomicInteger staleHits = new AtomicInteger();
        private final AtomicInteger discoveryFetches = new AtomicInteger();
        private final AtomicInteger discoveryFetchFailures = new AtomicInteger();

        @Override
        public void recordFetch() {
            fetches.incrementAndGet();
        }

        @Override
        public void recordFetchFailure() {
            fetchFailures.incrementAndGet();
        }

        @Override
        public void recordCacheHit() {
            cacheHits.incrementAndGet();
        }

        @Override
        public void recordStaleHit() {
            staleHits.incrementAndGet();
        }

        @Override
        public void recordDiscoveryFetch() {
            discoveryFetches.incrementAndGet();
        }

        @Override
        public void recordDiscoveryFetchFailure() {
            discoveryFetchFailures.incrementAndGet();
        }
    }

    private static String token(String secret, String payloadJson) {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url(payloadJson);
        String signingInput = header + "." + payload;
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(signingInput, secret));
    }

    private static String rs256Token(String kid, KeyPair keyPair, String payloadJson) {
        String headerJson = kid == null
                ? "{\"alg\":\"RS256\",\"typ\":\"JWT\"}"
                : "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}";
        String header = base64Url(headerJson);
        String payload = base64Url(payloadJson);
        String signingInput = header + "." + payload;
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(rsaSignature(signingInput, keyPair));
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String jwks(String kid, RSAPublicKey publicKey) {
        return """
                {"keys":[%s]}
                """.formatted(jwk(kid, publicKey));
    }

    private static String jwk(String kid, RSAPublicKey publicKey) {
        return """
                {"kty":"RSA","kid":"%s","use":"sig","alg":"RS256","n":"%s","e":"%s"}
                """.formatted(kid, base64Url(publicKey.getModulus().toByteArray()), base64Url(publicKey.getPublicExponent().toByteArray())).trim();
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(stripLeadingZero(value));
    }

    private static byte[] stripLeadingZero(byte[] value) {
        if (value.length > 1 && value[0] == 0) {
            return java.util.Arrays.copyOfRange(value, 1, value.length);
        }
        return value;
    }

    private static byte[] hmac(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] rsaSignature(String value, KeyPair keyPair) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(value.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

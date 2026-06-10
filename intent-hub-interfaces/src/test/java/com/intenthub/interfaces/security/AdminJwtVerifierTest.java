package com.intenthub.interfaces.security;

import com.intenthub.infrastructure.security.SecretRefResolver;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

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

    private static String token(String secret, String payloadJson) {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url(payloadJson);
        String signingInput = header + "." + payload;
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(signingInput, secret));
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
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
}

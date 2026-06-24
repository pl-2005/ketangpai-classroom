package com.ketangpai.security;

import com.ketangpai.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "jwt-test-secret-that-is-long-enough-for-hs384-signatures-2026";

    @Test
    void generatesAndParsesStrictClaims() {
        JwtUtil jwtUtil = createJwtUtil(60_000, "ketangpai-classroom", "ketangpai-web");

        String token = jwtUtil.generateToken(1L, "STUDENT", "bcrypt-hash-v1");
        JwtUtil.JwtClaims claims = jwtUtil.parseToken(token);

        assertThat(claims.userId()).isEqualTo(1L);
        assertThat(claims.role()).isEqualTo("STUDENT");
        assertThat(jwtUtil.isCredentialVersionValid(claims.credentialVersion(), "bcrypt-hash-v1"))
                .isTrue();
    }

    @Test
    void rejectsTamperedSignature() {
        JwtUtil jwtUtil = createJwtUtil(60_000, "ketangpai-classroom", "ketangpai-web");
        String token = jwtUtil.generateToken(1L, "STUDENT", "bcrypt-hash-v1");
        String[] segments = token.split("\\.");
        String signature = segments[2];
        String tamperedSignature = (signature.startsWith("A") ? "B" : "A")
                + signature.substring(1);
        String tampered = segments[0] + "." + segments[1] + "." + tamperedSignature;

        assertThatThrownBy(() -> jwtUtil.parseToken(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);
    }

    @Test
    void rejectsUnexpectedIssuer() {
        JwtUtil issuer = createJwtUtil(60_000, "issuer-a", "ketangpai-web");
        JwtUtil verifier = createJwtUtil(60_000, "issuer-b", "ketangpai-web");
        String token = issuer.generateToken(1L, "STUDENT", "bcrypt-hash-v1");

        assertThatThrownBy(() -> verifier.parseToken(token))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签发方");
    }

    @Test
    void rejectsNonHs256AlgorithmEvenWithValidSignature() {
        JwtUtil jwtUtil = createJwtUtil(60_000, "ketangpai-classroom", "ketangpai-web");
        Instant now = Instant.now();
        String token = Jwts.builder()
                .issuer("ketangpai-classroom")
                .subject("1")
                .claim("aud", "ketangpai-web")
                .claim("role", "STUDENT")
                .claim("ver", "version")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS384)
                .compact();

        assertThatThrownBy(() -> jwtUtil.parseToken(token))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("算法");
    }

    @Test
    void credentialVersionChangesWhenPasswordHashChanges() {
        JwtUtil jwtUtil = createJwtUtil(60_000, "ketangpai-classroom", "ketangpai-web");
        String token = jwtUtil.generateToken(1L, "STUDENT", "bcrypt-hash-v1");
        JwtUtil.JwtClaims claims = jwtUtil.parseToken(token);

        assertThat(jwtUtil.isCredentialVersionValid(claims.credentialVersion(), "bcrypt-hash-v2"))
                .isFalse();
    }

    @Test
    void refusesShortSigningSecretAtStartup() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "too-short");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 60_000L);
        ReflectionTestUtils.setField(jwtUtil, "issuer", "issuer");
        ReflectionTestUtils.setField(jwtUtil, "audience", "audience");

        assertThatThrownBy(jwtUtil::initKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32字节");
    }

    private JwtUtil createJwtUtil(long expiration, String issuer, String audience) {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", expiration);
        ReflectionTestUtils.setField(jwtUtil, "issuer", issuer);
        ReflectionTestUtils.setField(jwtUtil, "audience", audience);
        jwtUtil.initKey();
        return jwtUtil;
    }
}

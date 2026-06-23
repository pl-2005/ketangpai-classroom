package com.ketangpai.security;

import com.ketangpai.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 生成与校验。签名和标准时间声明交由 JJWT 处理。
 */
@Component
public class JwtUtil {

    private static final int MIN_SECRET_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 4096;
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 30;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.audience}")
    private String audience;

    private SecretKey signingKey;

    @PostConstruct
    void initKey() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 未配置");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET 至少需要256位（32字节）");
        }
        if (expiration <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION 必须大于0");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalStateException("JWT issuer/audience 未配置");
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId, String role, String passwordHash) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expiration);

        return Jwts.builder()
                .header().type("JWT").and()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("aud", audience)
                .claim("role", role)
                .claim("ver", credentialVersion(passwordHash))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public JwtClaims parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized("未提供认证令牌");
        }
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw unauthorized("认证令牌过长");
        }

        try {
            Jws<Claims> parsed = Jwts.parser()
                    .verifyWith(signingKey)
                    .clockSkewSeconds(ALLOWED_CLOCK_SKEW_SECONDS)
                    .build()
                    .parseSignedClaims(token);

            if (!"HS256".equals(parsed.getHeader().getAlgorithm())) {
                throw unauthorized("令牌签名算法无效");
            }

            Claims claims = parsed.getPayload();
            String subject = claims.getSubject();
            String tokenIssuer = claims.getIssuer();
            String role = requiredString(claims, "role");
            String version = requiredString(claims, "ver");

            if (subject == null || subject.isBlank() || tokenIssuer == null || tokenIssuer.isBlank()
                    || claims.getAudience() == null || claims.getAudience().size() != 1) {
                throw unauthorized("令牌缺少必要声明");
            }
            if (!issuer.equals(tokenIssuer) || !claims.getAudience().contains(audience)) {
                throw unauthorized("令牌签发方或受众无效");
            }

            Date issuedAt = claims.getIssuedAt();
            Date expiresAt = claims.getExpiration();
            if (issuedAt == null || expiresAt == null || !expiresAt.after(issuedAt)) {
                throw unauthorized("令牌时间声明无效");
            }
            Instant latestAllowedIssueTime = Instant.now().plusSeconds(ALLOWED_CLOCK_SKEW_SECONDS);
            if (issuedAt.toInstant().isAfter(latestAllowedIssueTime)) {
                throw unauthorized("令牌签发时间无效");
            }
            if (expiresAt.getTime() - issuedAt.getTime() > expiration + 1000) {
                throw unauthorized("令牌有效期无效");
            }

            Long userId = Long.valueOf(subject);
            if (userId <= 0) {
                throw unauthorized("令牌用户无效");
            }
            return new JwtClaims(userId, role, version);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw unauthorized("认证令牌无效");
        }
    }

    public boolean isCredentialVersionValid(String tokenVersion, String passwordHash) {
        if (tokenVersion == null || passwordHash == null) {
            return false;
        }
        byte[] actual = tokenVersion.getBytes(StandardCharsets.UTF_8);
        byte[] expected = credentialVersion(passwordHash).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }

    private String credentialVersion(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("密码哈希不能为空");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    private String requiredString(Claims claims, String name) {
        String value = claims.get(name, String.class);
        if (value == null || value.isBlank()) {
            throw unauthorized("令牌缺少必要声明");
        }
        return value;
    }

    private BusinessException unauthorized(String message) {
        return new BusinessException(401, message);
    }

    public record JwtClaims(Long userId, String role, String credentialVersion) {
    }
}

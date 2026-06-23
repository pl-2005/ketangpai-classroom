package com.ketangpai.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ketangpai.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * JWT 工具类 — 自实现 HMAC-SHA256，无需 jjwt-jackson（避免 Jackson 3 冲突）
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SecretKeySpec keySpec;

    @PostConstruct
    void initKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        keySpec = new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    /** 生成 JWT Token */
    public String generateToken(Long userId, String role) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(expiration);

        String headerJson = toJson(Map.of("alg", "HS256", "typ", "JWT"));
        String payloadJson = toJson(Map.of(
                "sub", userId.toString(),
                "role", role,
                "iat", now.getEpochSecond(),
                "exp", exp.getEpochSecond()
        ));

        String headerB64 = base64UrlEncode(headerJson);
        String payloadB64 = base64UrlEncode(payloadJson);
        String signingInput = headerB64 + "." + payloadB64;
        String signature = base64UrlEncode(hmacSha256(signingInput));

        return signingInput + "." + signature;
    }

    /** 验证并解析 Token，返回 claims */
    public JwtClaims parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(401, "未提供认证令牌");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException(401, "令牌格式无效");
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSig = base64UrlEncode(hmacSha256(signingInput));
        if (!expectedSig.equals(parts[2])) {
            throw new BusinessException(401, "令牌签名无效");
        }

        Map<String, Object> payload = fromJson(base64UrlDecode(parts[1]), new TypeReference<>() {});

        long exp = ((Number) payload.get("exp")).longValue();
        if (Instant.now().getEpochSecond() > exp) {
            throw new BusinessException(401, "令牌已过期");
        }

        Long userId = Long.valueOf((String) payload.get("sub"));
        String role = (String) payload.get("role");

        return new JwtClaims(userId, role);
    }

    private byte[] hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC 签名计算失败", e);
        }
    }

    private String base64UrlEncode(String data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private String base64UrlDecode(String data) {
        return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    private Map<String, Object> fromJson(String json, TypeReference<Map<String, Object>> typeRef) {
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw new BusinessException(401, "令牌解析失败");
        }
    }

    /** JWT 解析结果 */
    public record JwtClaims(Long userId, String role) {}
}

package com.ketangpai.security;

import com.ketangpai.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class RedisLoginAttemptService implements LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(RedisLoginAttemptService.class);
    private static final String KEY_PREFIX = "auth:login:";
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count;",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final int maxAttemptsPerIdentity;
    private final int maxAttemptsPerIp;
    private final long windowMillis;

    public RedisLoginAttemptService(
            StringRedisTemplate redisTemplate,
            @Value("${security.login.max-attempts-per-identity:5}") int maxAttemptsPerIdentity,
            @Value("${security.login.max-attempts-per-ip:30}") int maxAttemptsPerIp,
            @Value("${security.login.window-millis:900000}") long windowMillis) {
        this.redisTemplate = redisTemplate;
        this.maxAttemptsPerIdentity = maxAttemptsPerIdentity;
        this.maxAttemptsPerIp = maxAttemptsPerIp;
        this.windowMillis = windowMillis;
    }

    @PostConstruct
    void validateConfiguration() {
        if (maxAttemptsPerIdentity < 1 || maxAttemptsPerIp < maxAttemptsPerIdentity || windowMillis < 1000) {
            throw new IllegalStateException("登录限流配置无效");
        }
    }

    @Override
    public void checkAllowed(String username, String clientIp) {
        try {
            if (readCount(identityKey(username, clientIp)) >= maxAttemptsPerIdentity
                    || readCount(ipKey(clientIp)) >= maxAttemptsPerIp) {
                throw new BusinessException(429, "登录尝试过于频繁，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("登录限流Redis读取失败，将暂时放行本次请求，type={}",
                    e.getClass().getSimpleName());
        }
    }

    @Override
    public void recordFailure(String username, String clientIp) {
        try {
            increment(identityKey(username, clientIp));
            increment(ipKey(clientIp));
        } catch (DataAccessException e) {
            log.error("登录限流Redis写入失败，type={}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void recordSuccess(String username, String clientIp) {
        try {
            redisTemplate.delete(identityKey(username, clientIp));
        } catch (DataAccessException e) {
            log.error("登录限流Redis清理失败，type={}", e.getClass().getSimpleName());
        }
    }

    private int readCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("登录限流计数格式异常，key={}", key);
            return maxAttemptsPerIp;
        }
    }

    private void increment(String key) {
        redisTemplate.execute(INCREMENT_WITH_EXPIRY, List.of(key), Long.toString(windowMillis));
    }

    private String identityKey(String username, String clientIp) {
        return KEY_PREFIX + "identity:" + hash(normalizeUsername(username) + "\0" + normalizeIp(clientIp));
    }

    private String ipKey(String clientIp) {
        return KEY_PREFIX + "ip:" + hash(normalizeIp(clientIp));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }
}

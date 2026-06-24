package com.ketangpai.security;

import com.ketangpai.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisLoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new RedisLoginAttemptService(redisTemplate, 5, 30, 900_000);
        service.validateConfiguration();
    }

    @Test
    void allowsRequestBelowBothLimits() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("4", "29");

        assertThatCode(() -> service.checkAllowed("zhangsan", "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksIdentityAtConfiguredLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("5");

        assertThatThrownBy(() -> service.checkAllowed("zhangsan", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(429);
    }

    @Test
    void recordsBothIdentityAndIpFailuresAtomically() {
        service.recordFailure("zhangsan", "127.0.0.1");

        verify(redisTemplate, times(2)).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.<String>anyList(),
                eq("900000"));
    }

    @Test
    void successOnlyClearsIdentityFailureWindow() {
        service.recordSuccess("zhangsan", "127.0.0.1");

        verify(redisTemplate).delete(anyString());
    }

    @Test
    void redisOutageFailsOpen() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));

        assertThatCode(() -> service.checkAllowed("zhangsan", "127.0.0.1"))
                .doesNotThrowAnyException();
    }
}

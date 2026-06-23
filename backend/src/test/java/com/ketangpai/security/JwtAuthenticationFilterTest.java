package com.ketangpai.security;

import com.ketangpai.model.entity.User;
import com.ketangpai.model.enums.UserRole;
import com.ketangpai.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET = "jwt-test-secret-at-least-256-bits-long-for-filter";

    @Mock
    private UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesUsingCurrentDatabaseRole() throws Exception {
        JwtUtil jwtUtil = createJwtUtil();
        User user = user("bcrypt-hash-v1", UserRole.STUDENT, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        runFilter(jwtUtil, jwtUtil.generateToken(1L, "STUDENT", "bcrypt-hash-v1"));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    void rejectsTokenAfterPasswordHashChanges() throws Exception {
        JwtUtil jwtUtil = createJwtUtil();
        User user = user("bcrypt-hash-v2", UserRole.STUDENT, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        runFilter(jwtUtil, jwtUtil.generateToken(1L, "STUDENT", "bcrypt-hash-v1"));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsTokenAfterRoleChanges() throws Exception {
        JwtUtil jwtUtil = createJwtUtil();
        User user = user("bcrypt-hash-v1", UserRole.TEACHER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        runFilter(jwtUtil, jwtUtil.generateToken(1L, "STUDENT", "bcrypt-hash-v1"));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsTokenForDisabledAccount() throws Exception {
        JwtUtil jwtUtil = createJwtUtil();
        User user = user("bcrypt-hash-v1", UserRole.STUDENT, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        runFilter(jwtUtil, jwtUtil.generateToken(1L, "STUDENT", "bcrypt-hash-v1"));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void runFilter(JwtUtil jwtUtil, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        new JwtAuthenticationFilter(jwtUtil, userRepository).doFilterInternal(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );
    }

    private User user(String passwordHash, UserRole role, boolean enabled) {
        User user = User.builder()
                .username("zhangsan")
                .password(passwordHash)
                .role(role)
                .enabled(enabled)
                .deleted(false)
                .build();
        user.setId(1L);
        return user;
    }

    private JwtUtil createJwtUtil() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 60_000L);
        ReflectionTestUtils.setField(jwtUtil, "issuer", "ketangpai-classroom");
        ReflectionTestUtils.setField(jwtUtil, "audience", "ketangpai-web");
        jwtUtil.initKey();
        return jwtUtil;
    }
}

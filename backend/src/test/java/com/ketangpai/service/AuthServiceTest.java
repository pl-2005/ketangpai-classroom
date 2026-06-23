package com.ketangpai.service;

import com.ketangpai.dto.auth.LoginRequest;
import com.ketangpai.dto.auth.LoginResponse;
import com.ketangpai.dto.auth.RegisterRequest;
import com.ketangpai.dto.auth.RegisterResponse;
import com.ketangpai.model.entity.User;
import com.ketangpai.model.enums.UserRole;
import com.ketangpai.repository.UserRepository;
import com.ketangpai.security.AuthenticationAuditService;
import com.ketangpai.security.JwtUtil;
import com.ketangpai.security.LoginAttemptService;
import com.ketangpai.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private AuthenticationAuditService authenticationAuditService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerHashesPasswordAndNormalizesOptionalFields() {
        RegisterRequest request = new RegisterRequest(
                "zhangsan",
                "Abc123456!",
                "  ZHANGSAN@EXAMPLE.COM  ",
                "   ",
                UserRole.STUDENT
        );
        when(userRepository.existsByUsername("zhangsan")).thenReturn(false);
        when(userRepository.existsByEmail("zhangsan@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Abc123456!")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        RegisterResponse response = authService.register(request);

        assertThat(response.id()).isEqualTo(1L);
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getPassword().equals("bcrypt-hash")
                        && user.getEmail().equals("zhangsan@example.com")
                        && user.getRealName() == null));
    }

    @Test
    void loginSupportsUsersWithNullOptionalProfileFields() {
        User user = User.builder()
                .username("zhangsan")
                .password("bcrypt-hash")
                .role(UserRole.STUDENT)
                .enabled(true)
                .deleted(false)
                .build();
        user.setId(1L);
        when(userRepository.findByUsername("zhangsan")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Abc123456!", "bcrypt-hash")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "STUDENT", "bcrypt-hash")).thenReturn("jwt-token");

        LoginResponse response = authService.login(
                new LoginRequest("zhangsan", "Abc123456!"), "127.0.0.1");

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().realName()).isNull();
        assertThat(response.user().avatarUrl()).isNull();
        verify(loginAttemptService).recordSuccess("zhangsan", "127.0.0.1");
        verify(authenticationAuditService).loginSucceeded(user, "127.0.0.1");
    }

    @Test
    void unknownUsernameUsesDummyHashAndRecordsFailure() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("unknown", "Wrong123"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);

        verify(passwordEncoder).matches(org.mockito.ArgumentMatchers.eq("Wrong123"), anyString());
        verify(loginAttemptService).recordFailure("unknown", "127.0.0.1");
        verify(authenticationAuditService).loginFailed(
                "unknown", "127.0.0.1", AuthenticationAuditService.FailureReason.INVALID_CREDENTIALS);
    }

    @Test
    void wrongPasswordRecordsFailureWithoutIssuingToken() {
        User user = User.builder()
                .username("zhangsan")
                .password("bcrypt-hash")
                .role(UserRole.STUDENT)
                .enabled(true)
                .build();
        when(userRepository.findByUsername("zhangsan")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong123", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("zhangsan", "Wrong123"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);

        verify(loginAttemptService).recordFailure("zhangsan", "127.0.0.1");
        verify(jwtUtil, never()).generateToken(any(), anyString(), anyString());
    }

    @Test
    void blockedLoginDoesNotQueryUserRepository() {
        doThrow(new BusinessException(429, "登录尝试过于频繁，请稍后再试"))
                .when(loginAttemptService).checkAllowed("zhangsan", "127.0.0.1");

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("zhangsan", "Abc123456!"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(429);

        verifyNoInteractions(userRepository);
        verify(authenticationAuditService).loginBlocked("zhangsan", "127.0.0.1");
    }
}

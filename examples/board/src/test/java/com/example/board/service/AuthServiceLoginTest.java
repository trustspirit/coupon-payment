package com.example.board.service;

import com.example.board.dto.LoginRequest;
import com.example.board.entity.User;
import com.example.board.exception.BusinessException;
import com.example.board.exception.ErrorCode;
import com.example.board.repository.RefreshTokenRepository;
import com.example.board.repository.UserRepository;
import com.example.board.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtProvider jwtProvider;
    @Spy PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @InjectMocks AuthService authService;

    private User existingUser() {
        return User.builder()
                .email("a@example.com")
                .password(new BCryptPasswordEncoder().encode("password123"))
                .nickname("nick").build();
    }

    @Test
    void login_withValidCredentials_savesRefreshToken() {
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(existingUser()));
        when(jwtProvider.createAccessToken(any(), any())).thenReturn("access");
        when(jwtProvider.createRefreshToken(any())).thenReturn("refresh");

        var pair = authService.login(new LoginRequest("a@example.com", "password123"));

        assertThat(pair.accessToken()).isEqualTo("access");
        assertThat(pair.refreshToken()).isEqualTo("refresh");
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void login_withWrongPassword_throwsInvalidCredentials() {
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(existingUser()));

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@example.com", "wrong")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_withUnknownEmail_throwsInvalidCredentials() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("x@example.com", "password123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }
}

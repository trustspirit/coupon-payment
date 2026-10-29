package com.example.board.service;

import com.example.board.dto.SignupRequest;
import com.example.board.dto.UserResponse;
import com.example.board.entity.User;
import com.example.board.exception.BusinessException;
import com.example.board.exception.ErrorCode;
import com.example.board.repository.UserRepository;
import com.example.board.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock JwtProvider jwtProvider;
    @Spy PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks AuthService authService;

    @Test
    void signup_hashesPassword_andReturnsUser() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = authService.signup(
                new SignupRequest("a@example.com", "password123", "nick"));

        assertThat(res.email()).isEqualTo("a@example.com");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isNotEqualTo("password123"); // 해시됨
    }

    @Test
    void signup_withDuplicateEmail_throws() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("a@example.com", "password123", "nick")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_DUPLICATED);
    }
}

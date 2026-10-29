package com.example.board.service;

import com.example.board.dto.LoginRequest;
import com.example.board.dto.SignupRequest;
import com.example.board.dto.UserResponse;
import com.example.board.entity.RefreshToken;
import com.example.board.entity.User;
import com.example.board.exception.BusinessException;
import com.example.board.exception.ErrorCode;
import com.example.board.repository.RefreshTokenRepository;
import com.example.board.repository.UserRepository;
import com.example.board.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Value("${jwt.refresh-token-validity-ms}")
    private long refreshValidityMs;

    public record TokenPair(String accessToken, String refreshToken) {}

    @Transactional
    public UserResponse signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        User user = User.builder()
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .nickname(req.nickname())
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public TokenPair login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        String access = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refresh = jwtProvider.createRefreshToken(user.getId());

        // 한 사용자당 하나의 refresh 토큰만 유지: 재로그인 시 기존 것 폐기 후 저장
        refreshTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.save(RefreshToken.builder()
                .token(refresh)
                .userId(user.getId())
                .expiresAt(Instant.now().plusMillis(refreshValidityMs))
                .build());
        return new TokenPair(access, refresh);
    }

    @Transactional
    public String reissue(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        if (stored.getExpiresAt().isBefore(Instant.now()) || !jwtProvider.isValid(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return jwtProvider.createAccessToken(user.getId(), user.getEmail());
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}

package com.example.board.security;

import com.example.board.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtProviderTest {

    // 32바이트 이상 시크릿, 만료 access=1시간 refresh=2시간으로 생성해 검증
    private final JwtProvider provider =
            new JwtProvider("test-secret-value-that-is-at-least-32-bytes-long!!", 3_600_000L, 7_200_000L);

    @Test
    void createAccessToken_thenGetUserId_roundTrips() {
        String token = provider.createAccessToken(42L, "a@example.com");
        assertThat(provider.isValid(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo(42L);
    }

    @Test
    void getUserId_withTamperedToken_throws() {
        String token = provider.createAccessToken(42L, "a@example.com");
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThatThrownBy(() -> provider.getUserId(tampered))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void isValid_withExpiredToken_returnsFalse() {
        JwtProvider shortLived =
                new JwtProvider("test-secret-value-that-is-at-least-32-bytes-long!!", -1L, -1L);
        String token = shortLived.createAccessToken(1L, "a@example.com");
        assertThat(shortLived.isValid(token)).isFalse();
    }
}

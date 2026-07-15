package com.example.board.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class CookieUtil {

    public static final String ACCESS_COOKIE = "accessToken";
    public static final String REFRESH_COOKIE = "refreshToken";

    private final boolean secure;
    private final String sameSite;

    public CookieUtil(@Value("${app.cookie.secure}") boolean secure,
                      @Value("${app.cookie.same-site}") String sameSite) {
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie access(String token) {
        return base(ACCESS_COOKIE, token, Duration.ofMinutes(15)).build();
    }

    public ResponseCookie refresh(String token) {
        return base(REFRESH_COOKIE, token, Duration.ofDays(14)).build();
    }

    public ResponseCookie expire(String name) {
        return base(name, "", Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true).secure(secure).sameSite(sameSite)
                .path("/").maxAge(maxAge);
    }
}

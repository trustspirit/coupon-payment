package com.example.board.controller;

import com.example.board.dto.LoginRequest;
import com.example.board.dto.SignupRequest;
import com.example.board.dto.UserResponse;
import com.example.board.exception.BusinessException;
import com.example.board.exception.ErrorCode;
import com.example.board.security.CookieUtil;
import com.example.board.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(req));
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest req) {
        var pair = authService.login(req);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.access(pair.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.refresh(pair.refreshToken()).toString())
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request) {
        String refreshToken = readCookie(request, CookieUtil.REFRESH_COOKIE);
        if (refreshToken == null) throw new BusinessException(ErrorCode.INVALID_TOKEN);
        String newAccess = authService.reissue(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.access(newAccess).toString())
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        // /api/auth/** 는 permitAll 이라 토큰 없이도 도달 가능 → 멱등 처리.
        // 인증돼 있으면 서버측 refresh 토큰을 무효화하고, 어느 경우든 쿠키는 만료시킨다.
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            authService.logout(userId);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.expire(CookieUtil.ACCESS_COOKIE).toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.expire(CookieUtil.REFRESH_COOKIE).toString())
                .build();
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (var c : request.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}

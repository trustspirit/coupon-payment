package com.example.board.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    JwtProvider jwtProvider = mock(JwtProvider.class);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void validCookie_setsAuthentication() throws Exception {
        when(jwtProvider.isValid("good")).thenReturn(true);
        when(jwtProvider.getUserId("good")).thenReturn(7L);

        var req = new MockHttpServletRequest();
        req.setCookies(new Cookie("accessToken", "good"));
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
    }

    @Test
    void noCookie_leavesContextEmpty() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

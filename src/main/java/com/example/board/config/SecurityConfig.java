package com.example.board.config;

import com.example.board.exception.ErrorCode;
import com.example.board.security.JwtAuthenticationFilter;
import com.example.board.security.JwtProvider;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtProvider jwtProvider) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> writeError(res, 401, ErrorCode.INVALID_TOKEN))
                .accessDeniedHandler((req, res, ex) -> writeError(res, 403, ErrorCode.ACCESS_DENIED)))
            .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse res, int status, ErrorCode code)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"code\":\"" + code.getCode() + "\",\"message\":\"" + code.getMessage() + "\"}");
    }
}

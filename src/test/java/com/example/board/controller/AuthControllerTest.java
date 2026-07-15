package com.example.board.controller;

import com.example.board.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest extends PostgresTestContainer {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String json(Object o) throws Exception { return om.writeValueAsString(o); }

    @Test
    void signup_thenLogin_setsCookies() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "a@example.com", "password", "password123", "nickname", "nick"))))
            .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "a@example.com", "password", "password123"))))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("accessToken"))
            .andExpect(cookie().httpOnly("accessToken", true))
            .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "b@example.com", "password", "password123", "nickname", "nick"))));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "b@example.com", "password", "wrongpass"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void signup_invalidEmail_returns400() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "not-an-email", "password", "password123", "nickname", "nick"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}

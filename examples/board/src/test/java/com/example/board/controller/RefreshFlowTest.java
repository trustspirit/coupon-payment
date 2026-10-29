package com.example.board.controller;

import com.example.board.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshFlowTest extends PostgresTestContainer {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    private String json(Object o) throws Exception { return om.writeValueAsString(o); }

    @Test
    void refresh_withValidCookie_issuesNewAccessToken() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "r@example.com", "password", "password123", "nickname", "nick"))));
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "r@example.com", "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        Cookie refresh = login.getResponse().getCookie("refreshToken");

        mvc.perform(post("/api/auth/refresh").cookie(refresh))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("accessToken"));
    }

    @Test
    void refresh_withoutCookie_returns401() throws Exception {
        mvc.perform(post("/api/auth/refresh"))
            .andExpect(status().isUnauthorized());
    }
}

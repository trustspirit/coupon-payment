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
class PostControllerTest extends PostgresTestContainer {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String json(Object o) throws Exception { return om.writeValueAsString(o); }

    // 회원가입+로그인 후 accessToken 쿠키를 돌려준다.
    private Cookie loginAndGetAccessCookie(String email) throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", "password123", "nickname", "nick"))));
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        return res.getResponse().getCookie("accessToken");
    }

    @Test
    void createPost_authenticated_returns201() throws Exception {
        Cookie access = loginAndGetAccessCookie("author@example.com");

        mvc.perform(post("/api/posts").cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "hello", "content", "world"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("hello"));
    }

    @Test
    void createPost_unauthenticated_returns401() throws Exception {
        mvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "hello", "content", "world"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePost_byNonAuthor_returns403() throws Exception {
        Cookie author = loginAndGetAccessCookie("owner@example.com");
        MvcResult created = mvc.perform(post("/api/posts").cookie(author)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "t", "content", "c"))))
                .andExpect(status().isCreated()).andReturn();
        Integer id = om.readTree(created.getResponse().getContentAsString()).get("id").asInt();

        Cookie other = loginAndGetAccessCookie("intruder@example.com");
        mvc.perform(put("/api/posts/" + id).cookie(other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "hacked", "content", "hacked"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listPosts_public_returns200() throws Exception {
        mvc.perform(get("/api/posts")).andExpect(status().isOk());
    }
}

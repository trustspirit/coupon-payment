package com.example.board.controller;

import com.example.board.dto.PostRequest;
import com.example.board.dto.PostResponse;
import com.example.board.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public Page<PostResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return postService.list(pageable);
    }

    @GetMapping("/{id}")
    public PostResponse get(@PathVariable Long id) {
        return postService.get(id);
    }

    @PostMapping
    public ResponseEntity<PostResponse> create(Authentication auth,
                                               @Valid @RequestBody PostRequest req) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.create(userId, req));
    }

    @PutMapping("/{id}")
    public PostResponse update(Authentication auth, @PathVariable Long id,
                               @Valid @RequestBody PostRequest req) {
        Long userId = (Long) auth.getPrincipal();
        return postService.update(id, userId, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        Long userId = (Long) auth.getPrincipal();
        postService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}

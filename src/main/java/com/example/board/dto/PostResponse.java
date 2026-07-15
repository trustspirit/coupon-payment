package com.example.board.dto;

import com.example.board.entity.Post;
import java.time.Instant;

public record PostResponse(
        Long id, String title, String content,
        Long authorId, Instant createdAt, Instant updatedAt) {

    public static PostResponse from(Post p) {
        return new PostResponse(p.getId(), p.getTitle(), p.getContent(),
                p.getAuthorId(), p.getCreatedAt(), p.getUpdatedAt());
    }
}

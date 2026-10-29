package com.example.board.service;

import com.example.board.dto.PostRequest;
import com.example.board.entity.Post;
import com.example.board.exception.BusinessException;
import com.example.board.exception.ErrorCode;
import com.example.board.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository postRepository;
    @InjectMocks PostService postService;

    private Post postBy(Long authorId) {
        return Post.builder().title("t").content("c").authorId(authorId).build();
    }

    @Test
    void update_byNonAuthor_throwsAccessDenied() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(postBy(10L)));

        assertThatThrownBy(() -> postService.update(1L, 999L, new PostRequest("new", "new")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void update_byAuthor_succeeds() {
        Post post = postBy(10L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        var res = postService.update(1L, 10L, new PostRequest("new title", "new content"));

        assertThat(res.title()).isEqualTo("new title");
    }

    @Test
    void delete_byNonAuthor_throwsAccessDenied() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(postBy(10L)));

        assertThatThrownBy(() -> postService.delete(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void get_missingPost_throwsNotFound() {
        when(postRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.get(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }
}

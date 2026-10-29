package com.example.board.service;

import com.example.board.dto.PostRequest;
import com.example.board.dto.PostResponse;
import com.example.board.entity.Post;
import com.example.board.exception.BusinessException;
import com.example.board.exception.ErrorCode;
import com.example.board.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    @Transactional
    public PostResponse create(Long authorId, PostRequest req) {
        Post saved = postRepository.save(Post.builder()
                .title(req.title()).content(req.content()).authorId(authorId).build());
        return PostResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PostResponse get(Long id) {
        return PostResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> list(Pageable pageable) {
        return postRepository.findAll(pageable).map(PostResponse::from);
    }

    @Transactional
    public PostResponse update(Long postId, Long requesterId, PostRequest req) {
        Post post = findOrThrow(postId);
        verifyAuthor(post, requesterId);
        post.update(req.title(), req.content());
        return PostResponse.from(post);
    }

    @Transactional
    public void delete(Long postId, Long requesterId) {
        Post post = findOrThrow(postId);
        verifyAuthor(post, requesterId);
        postRepository.delete(post);
    }

    private Post findOrThrow(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    // 작성자 본인만 수정/삭제 허용. 남의 글이면 403(ACCESS_DENIED).
    private void verifyAuthor(Post post, Long requesterId) {
        if (!post.getAuthorId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}

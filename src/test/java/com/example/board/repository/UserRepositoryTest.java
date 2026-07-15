package com.example.board.repository;

import com.example.board.entity.User;
import com.example.board.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest extends PostgresTestContainer {

    @Autowired UserRepository userRepository;

    @Test
    void findByEmail_returnsSavedUser() {
        userRepository.save(User.builder()
                .email("repo-user@example.com").password("hash").nickname("nick").build());

        assertThat(userRepository.findByEmail("repo-user@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("repo-user@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("none@example.com")).isFalse();
    }
}

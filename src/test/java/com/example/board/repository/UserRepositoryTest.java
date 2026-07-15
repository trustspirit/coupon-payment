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
                .email("a@example.com").password("hash").nickname("nick").build());

        assertThat(userRepository.findByEmail("a@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("a@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("none@example.com")).isFalse();
    }
}

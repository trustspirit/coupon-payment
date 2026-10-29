package com.example.board.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 싱글톤 컨테이너 패턴: JVM당 한 번만 postgres를 띄우고 종료하지 않는다.
 * @Container/@Testcontainers 로 컨테이너를 클래스마다 stop 하면, Spring이 캐시한
 * @SpringBootTest 컨텍스트의 DB 커넥션이 끊겨 "Could not open EntityManager" 가 난다.
 * 컨테이너 정리는 Testcontainers Ryuk 이 JVM 종료 시 수행한다.
 */
public abstract class PostgresTestContainer {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }
}

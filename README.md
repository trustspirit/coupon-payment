# board — 게시판 REST API

Spring Boot 3 + PostgreSQL 게시판 API. JWT를 HttpOnly 쿠키에 담아 인증한다.

## 실행

```bash
docker compose up -d      # PostgreSQL 기동
./gradlew bootRun         # http://localhost:8080
```

> **JDK 주의**: 이 프로젝트는 Java 21이 필요하다. `brew install openjdk@21`은 keg-only라
> PATH에 없으므로, `./gradlew` 실행 시 `JAVA_HOME`을 지정해야 한다:
> ```bash
> JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew bootRun
> ```
> 매번 치기 싫으면 셸 프로필(`~/.zshrc`)에 `export JAVA_HOME=/opt/homebrew/opt/openjdk@21` 추가.

## 환경변수

- `JWT_SECRET`: JWT 서명 시크릿 (최소 32바이트, 운영 시 필수 설정)

## API

| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|:--:|
| POST | /api/auth/signup | 회원가입 | ✗ |
| POST | /api/auth/login | 로그인(쿠키 발급) | ✗ |
| POST | /api/auth/refresh | Access 재발급 | 쿠키 |
| POST | /api/auth/logout | 로그아웃 | ✓ |
| GET | /api/posts | 목록(페이징) | ✗ |
| GET | /api/posts/{id} | 단건 | ✗ |
| POST | /api/posts | 작성 | ✓ |
| PUT | /api/posts/{id} | 수정(작성자) | ✓ |
| DELETE | /api/posts/{id} | 삭제(작성자) | ✓ |

## 테스트

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew test
```

> Docker가 실행 중이어야 한다 (Testcontainers가 실제 PostgreSQL 컨테이너를 띄운다).

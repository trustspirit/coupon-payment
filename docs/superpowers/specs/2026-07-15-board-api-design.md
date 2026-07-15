# 설계 문서: spring-server (게시판 REST API)

- **작성일**: 2026-07-15
- **목적**: Spring Boot 학습/연습 — 단계별로 진행하며 핵심 비즈니스 로직은 학습자가 직접 작성
- **위치**: `/Users/shinyoung/Workspace/spring-server`

---

## 1. 개요

게시판(Board)을 소재로 한 REST API 백엔드 서버. 게시글 CRUD와 회원가입/로그인(JWT 인증)을 제공한다. 학습이 주 목적이므로 대부분의 입문 자료와 매칭되는 **계층형(Layered) 아키텍처**를 사용한다.

### 요구사항 요약

| 항목 | 결정 |
|------|------|
| 목적 | Spring 학습/연습 (단계별, 핵심 로직 직접 작성) |
| 도메인 | 게시판(Board) |
| 기능 | 게시글 CRUD + 회원가입/로그인 |
| DB | PostgreSQL |
| 인증 | JWT + Refresh Token, **HttpOnly 쿠키**에 저장 |
| 패키지 구조 | 계층형(Layered) |

---

## 2. 기술 스택 & 환경

| 구성 | 선택 | 이유 |
|------|------|------|
| 언어/런타임 | Java 21 (LTS) | Spring Boot 3.x 최소 요구 17+, 21이 최신 LTS |
| 프레임워크 | Spring Boot 3.x | 최신 안정 버전 (스캐폴딩 시 확정) |
| 빌드 도구 | Gradle (Groovy DSL) | 입문 자료와 매칭, 의존성 관리 간결 |
| DB | PostgreSQL | 실제 운영급 DB |
| 로컬 DB 실행 | Docker Compose | 설치 부담 없이 `docker compose up`으로 기동 |
| 주요 의존성 | Spring Web, Spring Data JPA, Spring Security, Validation, PostgreSQL Driver, jjwt(JWT), Lombok | REST + ORM + 인증 |

### 사전 환경 확인 (구현 첫 단계)

현재 개발 머신에 다음이 없을 수 있으므로 구현 시작 시 확인·설치한다:
- **Java 21** (sdkman 또는 Homebrew)
- **Gradle** (또는 Gradle Wrapper 사용)
- **Docker** (PostgreSQL 로컬 실행용)

---

## 3. 패키지 구조 (계층형)

```
spring-server/
├── docker-compose.yml            # postgres 로컬 실행
├── build.gradle
├── settings.gradle
└── src/
    ├── main/java/com/example/board/
    │   ├── BoardApplication.java     # 진입점
    │   ├── config/                   # SecurityConfig, CorsConfig
    │   ├── controller/               # PostController, AuthController
    │   ├── service/                  # PostService, AuthService  ← 비즈니스 로직 (직접 작성 지점)
    │   ├── repository/               # PostRepository, UserRepository, RefreshTokenRepository
    │   ├── entity/                   # Post, User, RefreshToken
    │   ├── dto/                      # 요청/응답 DTO
    │   ├── security/                 # JwtProvider, JwtAuthenticationFilter  ← 직접 작성 지점
    │   └── exception/                # 전역 예외 처리(@RestControllerAdvice)
    ├── main/resources/
    │   └── application.yml
    └── test/java/com/example/board/  # 단위/통합 테스트
```

---

## 4. 데이터 모델

```
User                          Post                        RefreshToken
─────                         ─────                       ─────────────
id (PK)                       id (PK)                     id (PK)
email (unique)          1  N  title                       token (unique)
password (BCrypt 해시)  ───→  content                     user_id (FK) ─→ User
nickname                      author_id (FK) ─→ User      expires_at
created_at                    created_at
                              updated_at
```

- **User ↔ Post**: 1:N (한 사용자가 여러 게시글 작성)
- 비밀번호는 **BCrypt 해시**로 저장 (평문 저장 금지)
- **RefreshToken은 DB 저장** → 로그아웃/재발급/탈취 대응

---

## 5. API 엔드포인트

### 인증 (AuthController)

| 메서드 | 경로 | 설명 | 인증 필요 |
|--------|------|------|:--:|
| POST | `/api/auth/signup` | 회원가입 | ✗ |
| POST | `/api/auth/login` | 로그인 → 쿠키에 토큰 발급 | ✗ |
| POST | `/api/auth/refresh` | Refresh 토큰으로 Access 재발급 | ✗(쿠키) |
| POST | `/api/auth/logout` | 쿠키 삭제 + Refresh 무효화 | ✓ |

### 게시글 (PostController)

| 메서드 | 경로 | 설명 | 인증 필요 |
|--------|------|------|:--:|
| GET | `/api/posts` | 목록 (페이징) | ✗ |
| GET | `/api/posts/{id}` | 단건 조회 | ✗ |
| POST | `/api/posts` | 작성 | ✓ |
| PUT | `/api/posts/{id}` | 수정 (작성자만) | ✓ |
| DELETE | `/api/posts/{id}` | 삭제 (작성자만) | ✓ |

---

## 6. 인증 흐름 (JWT + HttpOnly 쿠키)

### 설계 근거: 왜 HttpOnly 쿠키인가

- **Authorization 헤더 방식**: 토큰을 JS가 보관 → XSS 공격에 취약(토큰 탈취 가능)
- **HttpOnly 쿠키 방식**: JS가 읽을 수 없음 → XSS로부터 토큰 보호. 대신 CSRF에 노출 → `SameSite` 속성으로 방어
- 웹 브라우저 클라이언트에는 **HttpOnly + Secure + SameSite=Strict** 조합이 현대적 권장안

### 쿠키 속성

- `HttpOnly`, `Secure`, `SameSite=Strict` (크로스도메인 필요 시 `Lax`)
- Access Token 만료: **15분**
- Refresh Token 만료: **14일**
- CSRF 방어: `SameSite`에 의존 (학습용으로 충분, 추후 CSRF 토큰 확장 가능)

### 흐름

```
[로그인] POST /api/auth/login (email, password)
   → 비밀번호 검증(BCrypt)
   → Access Token(15분) + Refresh Token(14일) 생성
   → Set-Cookie: accessToken (HttpOnly, Secure, SameSite=Strict)
                 refreshToken (HttpOnly, Secure, SameSite=Strict)
   → RefreshToken을 DB에 저장

[인증된 요청] 브라우저가 쿠키 자동 전송
   → JwtAuthenticationFilter가 accessToken 쿠키 검증
   → 유효하면 SecurityContext에 인증 정보 저장

[토큰 만료] Access 만료 시 401
   → POST /api/auth/refresh (refreshToken 쿠키)
   → DB의 RefreshToken과 대조 → 새 Access 발급

[로그아웃] DB RefreshToken 삭제 + 쿠키 만료 처리
```

---

## 7. 에러 처리 & 테스트

### 에러 처리

- `@RestControllerAdvice`로 일관된 JSON 에러 응답: `{ "code", "message" }`
- 유효성 검증 실패 → 400
- 인증 실패 → 401
- 권한 없음(작성자 아님) → 403
- 리소스 없음 → 404

### 테스트

- 학습 목적에 맞춰 핵심 계층 중심
- Service 단위 테스트: JUnit 5 + Mockito
- 주요 API 통합 테스트: `@SpringBootTest` + MockMvc
- TDD로 진행 권장

---

## 8. 학습 모드: 학습자가 직접 작성할 지점

보일러플레이트는 도구가 작성하고, **의미 있는 결정이 담긴 5~10줄**은 학습자가 직접 작성한다:

1. `PostService`의 **수정/삭제 시 작성자 본인 확인 로직** (인가 정책)
2. `JwtProvider`의 **토큰 만료 시간·클레임 설계**
3. 회원가입 시 **중복 이메일·비밀번호 규칙 검증 전략**

---

## 9. 범위 밖 (YAGNI)

이번 구현에 포함하지 않는다 (추후 확장 가능):

- 댓글, 좋아요, 팔로우
- 파일/이미지 업로드
- 이메일 인증, 비밀번호 재설정
- CSRF 토큰 기반 방어 (현재는 SameSite로 갈음)
- 관리자 권한/롤 세분화
- 배포(CI/CD, 클라우드)

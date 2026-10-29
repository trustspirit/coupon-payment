# 설계 문서: 커머스 MSA 모노레포 (인증 · 쿠폰 · 주문/결제 Saga)

- **작성일**: 2026-10-29
- **상태**: 설계 초안 — 빌더 체크 반영 완료, 사용자 검토 대기
- **진행 현황**: [`docs/learning/PROGRESS.md`](../../learning/PROGRESS.md)
- **포함하는 기존 문서**
  - [한정 쿠폰 발급 설계](2026-10-29-limited-coupon-design.md): 내용은 유효. 위치만 단일 앱에서 `catalog-service`로 옮김 (§7 참고)
  - [주문·결제 초안](2026-10-29-coupon-order-saga-draft.md): 열린 질문 8개는 Phase 4 시작 전에 브레인스토밍으로 정함

> 표기: **[사용자 결정]** = 사용자가 직접 정함 · **[Claude 결정]** = Claude가 정한 기본값(바꿔도 됨) · **[빌더 재량]** = 구현하면서 정해도 됨 · **[미정]** = 해당 Phase 전에 사용자와 정함

---

## 1. 의도

Spring 학습 프로젝트다. 이 저장소를 **Gradle 멀티모듈 모노레포**로 바꾸고, 실무 커머스에서 흔한 구조(업계에 공개된 일반 패턴 기준)를 작은 규모로 직접 만들어 본다.

배우려는 것:
1. **서비스 분리와 인증**: 한 번 로그인으로 모든 서비스를 쓰는 구조 (Gateway + 인증 서비스 + 리소스 서버)
2. **동시성과 트래픽 방어**: 선착순 쿠폰 발급 (기존 쿠폰 설계)
3. **분산 정합성**: 서비스 사이의 틈을 메우는 Saga, 보상, Outbox, 대사
4. **동기/비동기 경계**: 사용자가 기다리는 경로는 동기 HTTP, 기다리지 않는 경로는 Kafka

### 작업 방식 [사용자 결정]
- **사용자가 직접 한 단계씩 구현한다.** 에이전트는 보조한다.
- 에이전트의 역할: 단계 목표 설명 → 학습할 개념 안내 → 완료 기준(확인 방법) 제시 → 힌트 → 사용자가 짠 코드 리뷰(진짜 문제인지 확인한 뒤 지적)
- **테스트는 당분간 작성하지 않고 나중에 돌아온다** [사용자 결정]. 자세한 내용은 §8 참고
- 사용자가 요청하지 않으면 에이전트가 제품 코드를 대신 작성하지 않는다. 사용자가 요청하면 빌드 설정과 docker-compose 같은 인프라 보일러플레이트는 도와도 된다.
- 한 번에 한 단계씩 진행한다. 단계가 끝날 때마다 `PROGRESS.md`를 갱신한다.

---

## 2. 목표 / 비목표 / 안티목표

### 목표
- 서비스마다 별도 Spring Boot 앱으로 실행하고, 서비스마다 자기 DB를 가진다
- 클라이언트는 Gateway 한 곳(`:8080`)만 알고, 한 번 로그인으로 모든 서비스를 쓴다
- 쿠폰 발급 설계의 불변식(최대 100장, 사용자당 1장)을 `catalog-service`에서 그대로 지킨다
- 주문 → 재고·쿠폰 예약 → 결제 → 확정의 Saga와 실패 시 보상이 동작한다. 서버가 재시작돼도 이어서 처리한다
- 주문 완료와 취소 이벤트가 Kafka로 나가고, 소비자가 멱등하게 처리한다

### 비목표
- UI
- 실제 배포(K8s, 클라우드), 서비스 디스커버리(Eureka 등). 주소는 설정값으로 고정한다
- 실제 PG 연동. 가짜 PG로 대체한다
- 정산, 포인트, 통계 소비자의 실제 비즈니스 로직. Phase 5에서 알림 소비자 하나만 만든다
- OAuth2 Authorization Server 표준 전체 구현(소셜 로그인, authorization code flow 등)

### 안티목표 (돌아가도 실패로 본다)
- 서비스가 다른 서비스의 DB나 테이블에 직접 접근하는 것
- 여러 서비스가 같은 도메인 엔티티 모듈을 공유하는 것 (분산 모놀리스)
- 서비스들이 서명 비밀키를 나눠 갖는 것 (대칭키 공유)
- Gateway가 넘긴 헤더만 믿고 서비스가 토큰을 검증하지 않는 것
- 요청마다 인증 서비스를 동기 호출하는 것 (인증 서비스가 장애 경로에 들어감)
- 시크릿과 개인키를 저장소에 커밋하는 것
- 필요성이 설명되지 않는 인프라 추가

---

## 3. 전체 구조

```
[클라이언트]
     │  쿠키(access, refresh)
     ▼
┌──────────┐  ① access 쿠키 JWT 1차 검증 (공개키, JWKS)
│ gateway  │  ② 쿠키 → Authorization: Bearer 로 변환해 전달
│  :8080   │  ③ 라우팅, 요청 ID, rate limit
└────┬─────┘
     ├──▶ account-service :8081   로그인/로그아웃/refresh/회원, RS256 서명, JWKS 공개
     ├──▶ catalog-service :8082   상품·재고·쿠폰 (발급, 예약/확정/원복)
     ├──▶ order-service   :8083   Saga 오케스트레이터, 주문 상태 머신, Outbox
     └──▶ payment-service :8084   PG 추상화, 멱등성, 웹훅 수신, 대사
                                        │ 동기 HTTP      ▲ 웹훅
                                        ▼                │
                                   fake-pg :8089 ────────┘

order-service ── 동기 HTTP ──▶ catalog-service (재고·쿠폰 예약/확정/원복)
order-service ── 동기 HTTP ──▶ payment-service (결제 승인/취소)
order-service ── Outbox → Kafka ──▶ notification-service :8085 (Phase 5)
```

- **동기 경로** [사용자 결정]: 주문 → 예약 → 결제 승인 → 확정. 결과를 클라이언트에 바로 돌려준다
- **비동기 경로** [사용자 결정]: 주문 완료/취소 이후의 알림 등은 Kafka 이벤트로 퍼뜨린다
- 포트 번호 [Claude 결정]

---

## 4. 저장소 구조

```
spring-server/
├── settings.gradle
├── gradle/libs.versions.toml       # 버전 카탈로그
├── build-logic/                    # convention plugin (공통 Java/Spring/테스트 설정)
├── examples/board/                 # 기존 board 앱 (git mv, 이력 유지, 독립 실행)
├── services/
│   ├── gateway/
│   ├── account-service/
│   ├── catalog-service/
│   ├── order-service/              # Phase 4
│   ├── payment-service/            # Phase 4
│   ├── fake-pg/                    # Phase 4
│   └── notification-service/       # Phase 5
├── libs/
│   ├── common-web/                 # 에러 응답 포맷, 공통 예외 핸들러
│   ├── security-resource-server/   # 리소스 서버 공통 설정 (JWKS 검증, userId 추출)
│   ├── event-contracts/            # Phase 5: Kafka 이벤트 스키마 (Java record)
│   └── outbox/                     # Phase 4~5: Outbox 테이블 + 릴레이
├── e2e-tests/                      # 여러 서비스를 띄워 시나리오 검증 (테스트 재개 후)
└── docker-compose.yml              # Postgres(서비스별 DB), Redis, Kafka(Phase 5)
```

### 모듈 규칙
- `services/*`만 실행 가능한 jar(`bootJar`)를 만든다. `libs/*`는 일반 jar다
- `libs`에는 **기술 공통과 계약만** 둔다. 도메인 엔티티는 각 서비스 안에만 둔다 [Claude 결정]
- 서비스 간 동기 HTTP의 요청/응답 DTO는 공유하지 않는다. 호출하는 쪽이 자기 DTO를 정의하고, 계약은 e2e 테스트로 확인한다 [Claude 결정]
- `event-contracts`는 하위 호환 규칙을 지킨다. 필드 추가는 되고, 이름 변경과 삭제는 안 된다
- 서비스 사이에 `project(':services:...')` 의존을 두지 않는다
- `libs` 모듈은 필요해지는 Phase에 만든다. 미리 만들어 두지 않는다

### 기술 스택
- Java 21, Spring Boot 3.4.x, Gradle 8.12 (기존 그대로)
- Spring Cloud 2024.0.x (Boot 3.4 호환 릴리스 트레인)
- Gateway: **Spring Cloud Gateway Server MVC** [사용자 결정, Claude 추천]
  - 이유: 지금까지 MVC만 썼으므로 리액티브(WebFlux)를 같이 배우는 부담을 피한다
  - 대가: 업계 자료는 WebFlux 버전이 더 많다. rate limit도 WebFlux 버전의 `RedisRateLimiter` 대신 **Bucket4j 기반 필터**를 쓴다
- 리소스 서버: `spring-boot-starter-oauth2-resource-server` (Nimbus JWT, JWKS 캐시)
- 토큰 발급: account-service에서 Nimbus JOSE(`spring-security-oauth2-jose`에 포함) 또는 기존 jjwt로 RS256 서명 [빌더 재량]
- 테스트: JUnit 5, Testcontainers (서비스마다 자기 컨테이너)

---

## 5. 인증 / 인가 설계

### 흐름
1. 클라이언트 → `POST gateway/api/auth/login` → account-service가 검증하고 access/refresh 토큰을 쿠키로 내려준다
2. 이후 요청은 브라우저가 쿠키를 자동으로 보낸다. 출처가 Gateway 하나라서 CORS와 쿠키 도메인 문제가 없다 [사용자 결정: 한 번 로그인]
3. Gateway는 access 쿠키의 JWT를 공개키로 1차 검증한다. 실패하면 401로 거절한다. 단, 공개 경로(로그인, 회원가입, refresh, 이벤트 단건 조회 등)는 통과시킨다
4. Gateway는 access 쿠키 값을 `Authorization: Bearer`로 바꿔 서비스에 전달하고, 쿠키 헤더에서 토큰을 제거한다 [Claude 결정]
5. 각 서비스는 Bearer 토큰을 **다시** 공개키로 검증한다 (이중 방어) [Claude 결정]
6. 서비스 간 호출(order → payment 등)은 받은 사용자 Bearer 토큰을 그대로 전달한다 [Claude 결정]

### 토큰
- 서명: **RS256** (비대칭) [Claude 결정, 안티목표에서 도출]. account-service만 개인키를 가진다
- JWKS: account-service가 `GET /.well-known/jwks.json`으로 공개키를 노출한다. 토큰 헤더에 `kid`를 넣는다
- Gateway와 서비스는 `jwk-set-uri`로 공개키를 가져와 캐시한다. 요청마다 account를 호출하지 않는다
- 클레임: `sub` = userId(Long의 문자열), `roles`, `iat`, `exp`, `iss` [Claude 결정]
- access 수명 15분, refresh 14일 (기존 board 값 유지)
- 기존 board의 refresh 동작: DB에 저장하고 사용자당 1개만 유지한다(재로그인 시 교체). `reissue`는 **access 토큰만** 새로 발급하며, refresh 회전과 재사용 감지는 **없다**. 이 동작을 그대로 이관한다. 회전과 재사용 감지는 Step 1.3에서 새로 추가한다 [사용자 결정]
- 키 관리 [Claude 결정]: 로컬과 테스트에서는 account-service가 기동할 때 RSA 키쌍을 메모리에 생성한다(재시작하면 기존 토큰 무효, 학습용으로 허용). 키 파일 경로 설정이 있으면 그 파일을 쓴다. 키 파일은 저장소에 커밋하지 않는다
- 키 회전(`kid` 여러 개 동시 노출)은 Phase 1의 선택 실습(Step 1.7)으로 둔다

### 쿠키 [Claude 결정]
- `accessToken`: `Path=/`, HttpOnly, 15분
- `refreshToken`: **`Path=/api/auth`**, HttpOnly, 14일. 기존 board는 `Path=/`라서 이관할 때 좁힌다 (refresh 토큰이 다른 서비스 요청에 섞이지 않게 하기 위해)
- `Secure`와 `SameSite`는 기존 설정값(`app.cookie.*`) 방식을 유지한다

### 인가
- **거친 인가(role)**: 토큰의 `roles` 클레임으로 각 서비스가 판단한다
- **세밀한 인가(소유권)**: 데이터를 가진 서비스가 판단한다. 예: "이 주문이 이 사용자 것인가"는 order-service가 판단한다
- 관리자 기능은 이번 범위에서 최소화한다. 시드 데이터나 테스트 셋업으로 대신한다

---

## 6. 데이터 / 인프라

- **Postgres 컨테이너 1개, 서비스별 DB** [Claude 결정]: `account_db`, `catalog_db`, `order_db`, `payment_db`. 서비스마다 DB 사용자를 따로 둔다. `docker-entrypoint-initdb.d` 초기화 스크립트로 만든다
- 기존 board는 기존 `board` DB를 그대로 쓴다
- Redis 7: Phase 2(쿠폰 Redis 게이트)부터. Gateway rate limit에도 쓴다
- Kafka (KRaft, 단일 브로커): Phase 5부터
- 스키마 관리: 기존 board와 같은 방식(JPA `ddl-auto`)으로 시작한다. Flyway 도입은 Phase 4 이전에 선택 실습으로 둔다 [빌더 재량]
- 시크릿: DB 비밀번호 등은 로컬 기본값을 `application-local.yml`과 docker-compose에 두되, 운영용 값은 환경변수로만 받는다. `.env.local`은 커밋하지 않는다

---

## 7. 구현 순서 (Phase / Step)

각 Step의 공통 완료 기준:
1. 사용자가 구현하고, 빌드가 성공하며, Step의 **수동 확인**(앱 실행 + curl/HTTP 요청)이 기대대로 나온다. 이미 있는 테스트(이관한 board 테스트 등)는 계속 통과해야 한다 (`JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew build`, Docker 실행 중)
2. 에이전트가 리뷰하고, 사용자가 반영 여부를 정한다
3. conventional commit으로 커밋한다 (AI 서명 trailer 금지)
4. `PROGRESS.md`의 Step 상태와 학습 노트를 갱신한다

### Phase 0 — 모노레포 전환
| Step | 내용 | 완료 기준 |
|---|---|---|
| 0.1 | 기존 코드를 `examples/board`로 이동 (`git mv`), 루트를 멀티모듈 루트로 전환 | `./gradlew :examples:board:test` 녹색 |
| 0.2 | `gradle/libs.versions.toml` + `build-logic` convention plugin | board가 convention plugin으로 빌드됨 |
| 0.3 | docker-compose: Postgres 서비스별 DB 초기화 스크립트, (선택) Redis | `docker compose up` 후 DB 4개 확인 |

학습 포인트: `settings.gradle`의 `include`, `bootJar`/`jar` 차이, `api` vs `implementation`, convention plugin

### Phase 1 — 인증 + Gateway (한 번 로그인)
| Step | 내용 | 완료 기준 |
|---|---|---|
| 1.1 | `account-service`: board의 회원가입/로그인/refresh/로그아웃을 **복사**해 이관 (board에 의존하지 않음) | 회원가입 → 로그인 → refresh → 로그아웃이 기존처럼 동작 |
| 1.2 | HS256 → RS256 전환, `kid`, JWKS 엔드포인트, refresh 쿠키 Path 축소 | JWKS 응답에 공개키가 보이고, 그 키로 토큰 서명 검증 가능 |
| 1.3 | **refresh 토큰 회전 + 재사용 감지** [사용자 결정]: refresh마다 새 refresh 발급, 이미 쓴 토큰이 다시 오면 탈취로 보고 그 사용자의 refresh를 전부 폐기 | 회전 후 이전 토큰으로 refresh → 401, 새 토큰도 무효화됨 |
| 1.4 | `libs/security-resource-server` + `catalog-service` 뼈대: 인증 필요한 API 하나(`GET /api/me/ping` 등) | 유효 토큰 200, 무효/만료 토큰 401 |
| 1.5 | `gateway`: 라우팅, 쿠키 JWT 1차 검증, 쿠키 → Bearer 변환, 요청 ID 헤더 | 쿠키 없이 401, 쿠키 있으면 catalog까지 도달 |
| 1.6 | 통합 확인: 로그인 1회 → Gateway 경유로 catalog 호출 성공, refresh 후 재호출 성공 | 수동 시나리오 (자동화 e2e는 테스트 재개 때) |
| 1.7 | (선택) 키 회전: `kid` 2개 노출, 이전 키로 서명된 토큰도 만료 전까지 통과 | |

학습 포인트: 대칭키 vs 비대칭키, JWKS와 캐시, refresh 회전과 재사용 감지, 리소스 서버, BFF 패턴, 이중 방어

### Phase 2 — 쿠폰 발급 (catalog-service)
[쿠폰 설계](2026-10-29-limited-coupon-design.md)의 단계 1~3을 `catalog-service`에서 진행한다. 내용(모델, API, 에러 코드, 불변식, 동시성 테스트, 학습 질문)은 그대로 유효하다.

| Step | 쿠폰 설계 단계 | 바뀌는 점 |
|---|---|---|
| 2.1 | 단계 1: 순진한 DB 구현 (race 재현) | userId는 JWT `sub`에서 얻는다 |
| 2.2 | 단계 2: 유니크 + 원자적 UPDATE + 메모리 체크 | 없음 |
| 2.3 | 단계 3: Redis Lua 게이트 + 워밍업 + 보상 | Redis가 docker-compose 공용 인스턴스가 된다 |

- 동시성 테스트는 서비스 계층을 직접 호출한다 (Gateway를 거치지 않음)
- 패키지 구조는 기존 계층형(`controller/service/repository/entity`)을 유지한다 [빌더 재량]

### Phase 3 — 트래픽 방어 (Gateway + catalog)
| Step | 쿠폰 설계 단계 | 바뀌는 점 |
|---|---|---|
| 3.1 | 단계 4: Rate Limiter | **Gateway의 Bucket4j(+Redis) 토큰 버킷**으로 쿠폰 발급 경로에 적용한다. 사용자별 키는 JWT `sub`. 쿠폰 설계의 직접 작성 고정 윈도우는 **선택 비교 실습**으로 둔다 [Claude 결정] |
| 3.2 | 단계 5: Circuit Breaker + 타임아웃 | catalog의 Redis 호출 CB는 그대로. Gateway 라우트 타임아웃을 추가한다 |
| 3.3 | 단계 6: 통계 | `RATE_LIMITED`는 Gateway에서 거절되므로 catalog 통계에 잡히지 않는다. Gateway 로그나 메트릭으로 따로 센다 |
| 3.4 | 단계 7: k6 부하 테스트 (선택) | Gateway 경유와 서비스 직접 호출을 비교한다 |

### Phase 4 — 주문/결제 Saga
**시작 전에 브레인스토밍을 한 번 더 한다.** [주문·결제 초안](2026-10-29-coupon-order-saga-draft.md)의 열린 질문 2~8을 정한다. 질문 1(서비스 분리)은 이 문서로 답이 나왔다.

이미 정해진 뼈대:
- 주문 서비스가 오케스트레이터다. "예약 → 결제 → 확정" 순서를 지휘하고, 실패하면 보상(예약 해제, 결제 취소)을 지시한다 [사용자 결정]
- 진행 상황은 주문 상태 머신에 기록한다. 재시작 후 그 기록을 보고 이어서 처리한다 [사용자 결정]
- 결제 서비스가 PG를 감싼다. PG 차이를 숨기고 멱등성(`Idempotency-Key`), 웹훅 수신, 대사를 책임진다 [사용자 결정]
- 가짜 PG는 지연, 실패, 타임아웃, 중복 웹훅을 설정으로 주입할 수 있다
- 틈을 메우는 장치: 타임아웃 뒤 조회해서 상태 확정, 만료된 예약 자동 해제 스케줄러, Outbox, 대사 배치
- catalog에 상품·재고 추가, `coupon_issue`에 `status`, `used_order_id`, `version` 추가 (쿠폰 초안 §5)

Step 분할은 브레인스토밍 후 이 문서에 추가한다 [미정]

### Phase 5 — Kafka + 이벤트 소비자
- docker-compose에 Kafka(KRaft) 추가, `libs/event-contracts`, `libs/outbox` 릴레이
- order-service가 주문 완료/취소 이벤트를 Outbox로 발행한다
- `notification-service`: 멱등 소비자, 재시도, DLT
- Step 분할은 Phase 4가 끝난 뒤 정한다 [미정]

---

## 8. 테스트 전략

### 지금: 테스트는 미룬다 [사용자 결정]
- Phase 0~1은 새 테스트를 쓰지 않고 수동 확인으로 진행한다. 이관한 board 테스트는 계속 통과해야 한다
- **Phase 2 시작 전에 테스트 방식을 다시 정한다.** 쿠폰 발급의 학습 핵심(race 재현, 불변식 검증)이 동시성 테스트 자체라서, Phase 2는 테스트 없이 진행하기 어렵다. 그때 정할 것: 누가 테스트를 쓰는지(에이전트가 실패하는 테스트 제공 / 사용자 작성 / 섞어서), Phase 0~1에 소급해서 추가할 테스트 범위
- 아래는 테스트를 재개할 때의 기준이다

- **서비스 단위**: 각 서비스가 자기 Testcontainers(Postgres, Redis)로 독립 테스트한다
- **인증 테스트 지원**: 리소스 서버 테스트에서는 테스트용 RSA 키로 토큰을 직접 만들거나 `spring-security-test`의 `jwt()` post-processor를 쓴다 [빌더 재량]
- **e2e** (테스트 재개 후): `e2e-tests` 모듈이 여러 서비스를 띄워 Gateway 경유 시나리오를 검증한다. 띄우는 방식(같은 JVM에서 여러 `SpringApplication` 기동, 또는 bootJar를 Testcontainers 이미지로 실행)은 [빌더 재량]이고, Step 1.5에서 정한다
- 브랜치의 테스트는 항상 녹색을 유지한다. race 재현 테스트는 `@Disabled`로 커밋한다 (쿠폰 설계 단계 1 정책)

---

## 9. 브랜치 / 커밋
- 브랜치 없이 `master`에서 바로 작업한다 [사용자 결정]. 푸시 전에 테스트 통과를 확인한다
- Step이 끝날 때마다 커밋한다. **커밋은 사용자가 한다.** 에이전트는 사용자가 요청할 때만 커밋하고, 메시지 초안은 제안할 수 있다
- Phase 0의 `git mv`는 이동만 담은 커밋과 빌드 수정 커밋을 나눈다 (이력 추적을 위해)

## 10. 빌더에게 남긴 것
- 패키지 이름 (예: `com.example.commerce.account`)
- convention plugin의 세부 구성
- RS256 서명 라이브러리 선택 (Nimbus vs jjwt)
- e2e 기동 방식
- Gateway 라우트 정의 방식 (yml vs Java DSL)
- 공개 경로 목록의 세부 구성
- 쿠폰 설계 §8에서 빌더에게 남긴 항목 전부

# 설계 문서: 한정 수량 쿠폰 발급 (선착순 100장)

- **작성일**: 2026-10-29
- **상태**: 설계 초안 — 사용자 검토 대기
- **진행 현황**: [`docs/learning/PROGRESS.md`](../../learning/PROGRESS.md)
- **후속 프로젝트**: [쿠폰 적용 주문·결제 초안](2026-10-29-coupon-order-saga-draft.md)

> **2026-10-29 위치 변경**: 이 설계는 [커머스 MSA 모노레포 설계](2026-10-29-commerce-msa-design.md)의 `catalog-service` 안에서 진행한다. 단계 1~3 → Phase 2, 단계 4~7 → Phase 3. 바뀌는 점(userId는 JWT `sub`, rate limit은 Gateway 토큰 버킷 우선 등)은 그 문서 §7을 따른다. 구현은 이제 **사용자가 직접** 하고 에이전트는 보조한다(§1의 '작업 방식'은 대체됨).

> 표기: **[사용자 결정]** = 사용자가 직접 정한 것, **[Claude 결정]** = Claude가 정한 기본값(바꿔도 됨), **[빌더 재량]** = 구현자가 판단해도 되는 것.

---

## 1. 의도 (왜 만드는가)

Spring 학습 프로젝트. 기존 board API(Spring Boot 3.4 + PostgreSQL + JWT 쿠키 인증) 위에 **선착순 한정 쿠폰 발급**을 얹는다.

목적은 기능 자체가 아니라 아래 세 가지를 **직접 깨뜨려 보고 → 고치면서** 체감하는 것이다.

1. **동시성**: 수많은 요청이 동시에 와도 정확히 100장만 발급
2. **멱등성 / 중복 방지**: 같은 사용자가 따닥 누르거나 재시도해도 1장
3. **트래픽 방어**: 10만 명이 오픈 시각에 몰려도 DB와 다른 기능(게시판, 로그인)이 무너지지 않음

학습 방식 **[사용자 결정]**: 가장 단순한 버전부터 만들고, 동시성 테스트로 문제를 재현한 뒤, 단계별로 도구(DB 제약 → Redis → Rate Limiter → Circuit Breaker)를 추가하며 "왜 이게 필요한지"를 확인한다.

작업 방식 (기존 프로젝트와 동일): Claude가 테스트로 동작을 고정한 뒤 구현까지 완성하고, **각 단계가 끝날 때마다 멈춰서** 사용자가 리뷰한다. 설계 판단 지점마다 ★ Insight로 이유를 설명한다.

---

## 2. 목표 / 비목표 / 안티목표

### 목표
- 이벤트(쿠폰) 1개당 한정 수량(100장), 오픈 시각 이전에는 발급 불가
- 사용자당 이벤트별 1장, 재요청 시 "이미 발급받음"
- 단계별로 동시성 테스트가 같은 불변식을 검증
- 단계 2(DB만)와 단계 3(Redis 게이트)의 차이를 **숫자로 측정**
- 시도 수 / 결과별 수 / 고유 사용자 수 / 매진 소요 시간 통계

### 비목표 (이번 범위에서 하지 않음)
- UI **[사용자 결정]**
- Kafka / SQS — 100장 시나리오에서는 정당화되지 않음. 후속 프로젝트(주문·결제)에서 다룸 **[사용자 결정]**
- 관리자용 이벤트 생성 API (테스트는 repository로, 로컬은 시드 데이터로) **[Claude 결정]**
- 대기열(waiting room) — 개념만 학습, 구현 안 함
- 실제 다중 인스턴스 배포, Redis Cluster/Sentinel
- 쿠폰 **사용**(주문 적용) — 후속 프로젝트에서 `coupon_issue`를 확장

### 안티목표 (기술적으로 돌아가도 실패로 간주)
- 어떤 상황에서도 101장 이상 발급되거나, 한 사용자가 2장을 받는 것
- 운에 따라 통과/실패하는 flaky 동시성 테스트
- Redis 장애 시 트래픽이 그대로 DB로 쏟아지는 fallback
- 필요성이 설명되지 않는 인프라 추가

---

## 3. 전체 흐름 (최종 형태)

```
요청
 │
 ▼
[JWT 인증]            기존 필터. userId 식별
 │
 ▼
[Rate Limiter]        사용자별 초당 N회 초과 → 429          (단계 4)
 │
 ▼
[메모리 체크]          오픈 전 → 409 / 이미 매진 → 409         (단계 2)
 │                     DB·Redis 접근 없이 즉시 거절
 ▼
[Redis Lua 게이트]     중복? 재고? → 원자적 차감 + 기록         (단계 3)
 │                     → 대부분 여기서 탈락 (409)
 ▼ 최대 100건
[DB 트랜잭션]          발급 INSERT(유니크) + 재고 UPDATE        (단계 2, 최종 방어선)
 │
 ▼
201 발급 완료
```

- Redis 호출부는 Circuit Breaker로 감싼다. 열리면 503으로 빠르게 실패한다(단계 5).
- 판정의 진실은 Redis, 영속성의 진실은 DB. DB 제약은 Redis가 틀려도 막는 **최종 방어선**이다.
- DB에는 "**최대** 100건, 정상 상황에서는 정확히 100건"이 들어간다.

---

## 4. 도메인 모델

### `coupon_event` **[Claude 결정: 이벤트 1개 = 쿠폰 1종, 테이블 1개]**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint PK | |
| name | varchar | |
| total_quantity | int | 100 |
| remaining_quantity | int | 원자적 차감 대상 (핫 로우) |
| open_at | timestamptz | 이 시각 이전엔 발급 불가 **[사용자 결정]** |
| created_at | timestamptz | |

- 종료 시각 없음. 소진되면 끝 **[Claude 결정]**
- 재고는 DB에 저장 **[사용자 결정]**

### `coupon_issue` **[사용자 결정: 유니크 제약이 있는 발급 테이블]**
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint PK | |
| event_id | bigint | |
| user_id | bigint | `Post.authorId`처럼 FK 없이 Long **[Claude 결정]** |
| issued_at | timestamptz | 매진 소요 시간 통계에 사용 |
| UNIQUE (event_id, user_id) | | 중복 발급 최종 방어 |

- 후속 프로젝트에서 `status`(ISSUED / RESERVED / USED), `used_order_id`, `version`을 추가할 예정이다. **이번에는 추가하지 않는다.**

### 시간
- `Clock` 빈을 주입해서 오픈 시각 테스트를 결정적으로 만든다 **[Claude 결정]**

---

## 5. API

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/coupon-events/{eventId}` | 불필요 | 이벤트 정보(이름, 총수량, 남은 수량, 오픈 시각) |
| POST | `/api/coupon-events/{eventId}/issuance` | 필요 | 쿠폰 발급 요청. 바디 없음 |
| GET | `/api/coupon-events/{eventId}/issuance/me` | 필요 | 내 발급 여부 (`{issued, issuedAt}`). 클라이언트 버튼 비활성화용 |
| GET | `/api/coupon-events/{eventId}/stats` | 필요 | 통계 (단계 6) |

- 성공 시 `201 Created` + `{eventId, issuedAt}`. Redis 단계에서도 DB 저장은 동기라 응답 의미가 바뀌지 않는다.
- SecurityConfig: 이벤트 단건 GET만 permitAll. `/issuance/me`와 `/stats`는 인증 필요 (matcher 순서 주의).

### 응답 / 에러 코드 **[사용자 결정: 재요청은 "이미 발급받음"]**
기존 `ErrorCode` + `GlobalExceptionHandler` 패턴을 따른다.

| 상황 | HTTP | code |
|---|---|---|
| 성공 | 201 | — |
| 이벤트 없음 | 404 | `COUPON_EVENT_NOT_FOUND` |
| 오픈 전 | 409 | `COUPON_EVENT_NOT_OPEN` |
| 이미 발급받음 | 409 | `COUPON_ALREADY_ISSUED` |
| 매진 | 409 | `COUPON_SOLD_OUT` |
| 요청 과다 (단계 4) | 429 + `Retry-After` | `TOO_MANY_REQUESTS` |
| Redis 장애 / CB open (단계 5) | 503 | `COUPON_SERVICE_UNAVAILABLE` |

> 멱등성 메모: 두 번째 요청은 서버 상태를 바꾸지 않으므로 409를 돌려줘도 HTTP 의미의 멱등성은 지켜진다. `Idempotency-Key` 헤더는 이번 범위에서 불필요하고, 후속 프로젝트(결제)에서 필요해진다.

---

## 6. 단계별 진행

각 단계의 공통 완료 기준:
1. 해당 단계의 테스트가 모두 통과한다 (`JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew test`, Docker 실행 중이어야 함)
2. conventional commit으로 커밋한다
3. 사용자가 리뷰한다
4. `docs/learning/PROGRESS.md`를 갱신한다

### 단계 전략 공존 **[Claude 결정]**
단계 2(DB)와 단계 3(Redis)의 구현을 모두 남겨서 비교할 수 있게 한다.
- `CouponIssuer` 인터페이스와 구현체 `DbCouponIssuer`, `RedisGateCouponIssuer`를 둔다.
- 설정 `app.coupon.issue-strategy: db | redis`로 선택한다.
- 동시성 테스트는 두 전략 모두에 대해 같은 불변식을 검증한다.

### 공통 동시성 테스트 설계
- 이벤트 수량 100, **고유 사용자 300명 × 각 2회 = 600 요청**을 동시에 실행한다. `ExecutorService` + 시작 `CountDownLatch`로 동시 출발시킨다. **[빌더 재량: 수치]**
- 서비스 계층을 직접 호출해 불변식을 검증하고, HTTP 상태 매핑은 MockMvc로 별도 검증한다.
- **불변식:**
  - `coupon_issue` 행 수 == 100
  - 모든 `(event_id, user_id)`는 1건 이하
  - `remaining_quantity` == 0
  - 성공 응답 수 == 100
  - (Redis 단계) Redis 재고 == 0, Redis 발급 집합 크기 == 100
- 결과별 카운트(성공, 중복, 매진)를 로그로 출력해 단계 간 비교에 쓴다.

---

### 단계 1 — 순진한 DB 구현 (문제 재현)
**구현**: 이벤트 조회 → `remaining > 0` 확인 → 발급 저장 → 엔티티 필드 차감(더티 체킹). 유니크 제약은 없다.

**보고 싶은 것**: 동시성 테스트에서 100장 초과 발급과 같은 사용자 중복 발급이 나는 것.

**main 테스트 정책**: 이 단계의 불변식 테스트는 실패하는 게 정상이다. 실패 결과(초과 발급 수)를 PROGRESS.md 학습 노트에 기록하고, 테스트는 `@Disabled("stage 1: race 재현용")`으로 커밋한다. 단계 2에서 활성화한다. **[Claude 결정: 브랜치의 테스트는 항상 녹색 유지]**

**학습 질문**
- `@Transactional`이 있는데 왜 초과 발급이 날까? PostgreSQL 기본 격리 수준(READ COMMITTED)에서 "확인 후 행동(check-then-act)"이 왜 깨질까?
- 더티 체킹으로 `remaining = remaining - 1`을 하면 실제로 어떤 SQL이 나갈까? 왜 Lost Update가 생길까?

### 단계 2 — DB로 정확성 확보 + 메모리 앞단 체크
**구현**
- `(event_id, user_id)` 유니크 제약을 건다.
- 원자적 차감 쿼리: `UPDATE coupon_event SET remaining_quantity = remaining_quantity - 1 WHERE id = ? AND remaining_quantity > 0` (`@Modifying` JPQL 또는 네이티브)
- 같은 트랜잭션에서 **INSERT 먼저(`saveAndFlush`) → UPDATE**를 실행한다 **[사용자 결정: 같은 트랜잭션 / Claude 결정: 순서]**
  - UPDATE 결과가 0행이면 `COUPON_SOLD_OUT` 예외를 던져 롤백한다
  - 유니크 위반은 `COUPON_ALREADY_ISSUED`로 변환한다. 이때 `DataIntegrityViolationException`은 트랜잭션 **바깥**에서 제약 이름으로 구분해 변환한다(트랜잭션 안에서 잡으면 세션이 오염되고 rollback-only가 됨) **[빌더 재량: 변환 위치]**
- **메모리 앞단 체크**
  - 오픈 시각: 이벤트의 `open_at`은 바뀌지 않으므로 인스턴스 메모리에 캐시한다(`ConcurrentHashMap`, 추가 의존성 없음)
  - 매진 플래그: 한 번 매진을 확인하면 이후 요청은 DB 없이 거절한다. 매진은 단조적인 상태라 인스턴스별 플래그로 충분하다

**측정 실험** (선택, 결과는 PROGRESS.md 학습 노트에 기록)
- INSERT 먼저 vs UPDATE 먼저 순서에 따른 소요 시간
- 원자적 UPDATE vs 비관적 락(`SELECT ... FOR UPDATE`) 비교
- 매진 플래그 유무에 따른 DB 쿼리 수 (Hibernate statistics 또는 쿼리 카운트)

**학습 질문**
- 왜 INSERT를 먼저 하면 핫 로우 락을 잡는 시간이 줄어들까?
- 동시 UPDATE가 같은 행을 노릴 때 PostgreSQL은 어떻게 동작할까(READ COMMITTED에서 행을 다시 읽고 조건을 재평가)? 그래서 `WHERE remaining > 0`이 안전한 이유는?
- 비관적 락, 낙관적 락(`@Version`), 원자적 UPDATE 중 핫 로우 하나에 가장 맞는 것은? 낙관적 락은 왜 여기서 나쁠까?
- 매진 플래그를 확인한 뒤에도 이미 커넥션 풀 대기열에 들어선 요청은 왜 막지 못할까?

### 단계 3 — Redis 선착순 게이트
**구현**
- 의존성: `spring-boot-starter-data-redis`(Lettuce), docker-compose에 `redis:7` 추가, Testcontainers Redis(`GenericContainer` + `@ServiceConnection(name = "redis")`) **[빌더 재량: 컨테이너 구성 방식]**
- **Redis 키** (`{eventId}` 해시 태그로 클러스터에서도 같은 슬롯에 배치)
  - `coupon:{eventId}:stock` — 남은 수량
  - `coupon:{eventId}:users` — 발급받은 userId 집합(Set)
- **Lua 스크립트** (원자적 판정): 이미 받았으면 `0`, 재고가 없으면 `-1`, 아니면 `DECR` + `SADD` 후 `1`을 반환한다
- 게이트를 통과(`1`)하면 단계 2의 DB 트랜잭션을 동기로 실행한다. DB 제약은 그대로 유지한다.
- **보상**: DB 저장이 실패하면(중복 제외) 보상 Lua(`SREM` + `INCR`)로 자리를 반환한다
- **워밍업** **[Claude 결정]**
  - 기동 시와 `@Scheduled`(1분 주기)에 "오픈 10분 전 ~ 오픈 이후" 이벤트의 재고를 `SET NX`로 적재한다. NX이므로 여러 인스턴스가 동시에 실행해도 안전하다.
  - 요청 시 키가 없으면 DB의 `remaining_quantity`로 `SET NX` 지연 초기화를 fallback으로 둔다
- 키 만료(TTL): 오픈 후 7일 **[빌더 재량]**
- 응답 매핑: Lua 반환값 `1` / `0` / `-1` → 201 / `ALREADY_ISSUED` / `SOLD_OUT`

**측정**: 단계 2와 같은 동시성 테스트를 실행해 소요 시간과 DB 도달 쿼리 수를 비교한다. 결과는 같고(정확히 100) DB 부하만 달라야 한다.

**학습 질문**
- 왜 `GET` → `DECR`을 애플리케이션에서 나눠 호출하면 안 되고 Lua여야 할까? `MULTI/EXEC`는 왜 부족할까?
- Redis 성공 뒤 DB 저장 전에 서버가 죽으면? (자리가 유실돼 "최대 100"이 됨) 이걸 맞추는 대사(reconciliation) 배치는 어떻게 설계할까? (개념만 학습, 구현은 비목표)
- Redis failover로 최근 쓰기가 유실되면 어떤 일이 생기고, DB 제약은 그중 무엇을 막아줄까?
- 보상으로 자리가 반환됐는데 어떤 인스턴스는 이미 메모리 매진 플래그를 세웠다면?

### 단계 4 — Rate Limiter (사용자별)
**구현** **[Claude 결정: 직접 작성한 Redis 고정 윈도우]**
- Lua `INCR` + `EXPIRE` 고정 윈도우. 키는 `ratelimit:coupon:{userId}:{epochSecond}`
- 발급 엔드포인트에만 적용한다(`HandlerInterceptor`). 초과 시 429 + `Retry-After`
- 한도는 설정값 `app.coupon.rate-limit.per-user-per-second` (기본 5) **[빌더 재량: 기본값]**
- 인메모리 limiter(Resilience4j RateLimiter)를 쓰지 않는 이유: 인스턴스마다 따로 세서 서버가 N대면 한도도 N배가 된다
- 대안으로 Bucket4j + Redis가 있다는 것을 비교 학습한다(구현은 하나만)

**학습 질문**
- 고정 윈도우의 경계 버스트 문제(0.99초와 1.01초에 몰아 보내기)는? 슬라이딩 윈도우, 토큰 버킷과 비교하면?
- rate limit 판정 자체도 Redis 호출 1회다. 앞단 비용을 어떻게 볼까?

### 단계 5 — Circuit Breaker + 타임아웃
**구현**
- `resilience4j-spring-boot3` + AOP. Redis 게이트와 rate limiter 호출을 감싼다
- Lettuce 커맨드 타임아웃을 짧게 설정한다(`spring.data.redis.timeout: 200ms`) **[빌더 재량: 값]**
- CB가 열리면 **빠르게 실패해서 503** **[Claude 결정]**. DB fallback은 하지 않는다(안티목표). rate limiter도 Redis 장애 시 503으로 일관되게 처리한다
- 테스트: 별도 Redis 컨테이너를 pause해서 장애를 재현하고 다음을 검증한다 **[빌더 재량: 장애 주입 방식]**
  - 응답이 503으로 빠르게 돌아오는지
  - CB 상태가 OPEN으로 바뀌는지
  - 컨테이너를 재개한 뒤 HALF_OPEN을 거쳐 CLOSED로 복구되는지

**학습 질문**
- 타임아웃 없이 CB만 있으면 왜 소용이 없을까?
- CLOSED → OPEN → HALF_OPEN 전이 조건(실패율, 슬라이딩 윈도우, 대기 시간)은 어떻게 정할까?
- 이 시나리오에서 DB fallback이 "정합성은 안전하지만 부하 측면에서 위험한" 이유는?

### 단계 6 — 통계
**구현**
- **결과별 카운터**: `SUCCESS`, `DUPLICATE`, `SOLD_OUT`, `NOT_OPEN`, `RATE_LIMITED`
  - Redis 게이트를 통과하는 요청: Lua 안에서 `INCR coupon:{eventId}:stats:{result}`로 센다(왕복 추가 없음)
  - 메모리에서 거절된 요청: 로컬 `LongAdder`에 모았다가 1초마다 Redis에 한 번에 더한다(배치 플러시)
  - `UNAVAILABLE`(Redis 장애)은 Redis에 기록할 수 없으므로 로그로만 남긴다
- **고유 사용자 수**: HyperLogLog `PFADD coupon:{eventId}:hll` (오차 약 0.81%, 최대 12KB). 메모리에서 거절된 요청의 userId도 배치로 `PFADD`한다 **[빌더 재량: 배치 구조]**
- **매진 소요 시간**: DB `MAX(issued_at) - open_at`
- `GET /api/coupon-events/{eventId}/stats` → `{attempts: {result: count}, uniqueUsers, issuedCount, soldOutDurationMs}`

**학습 질문**
- 왜 DB만으로는 "몇 명이 시도했나"를 알 수 없을까?
- Set과 HyperLogLog의 메모리와 정확도 트레이드오프는?
- 세는 행위 자체가 앞단 거절의 이점을 깎아먹지 않으려면?

### 단계 7 — 부하 테스트 (선택)
- k6 스크립트로 `issue-strategy=db`와 `redis`를 비교한다(p95 지연, 처리량, 에러율)
- 노트북 한계 때문에 10만 동시 요청 대신 축소 비율(예: 1만 요청, VU 수백)로 진행한다
- 실험: `spring.threads.virtual.enabled=true`(가상 스레드)를 켜면 병목이 Tomcat 스레드에서 커넥션 풀로 옮겨가는지 관찰한다

---

## 7. 로컬 환경

- `docker-compose.yml`에 `redis:7`을 추가한다(단계 3)
- 로컬 시드 데이터: `local` 프로필에서만 동작하는 러너가 "1분 뒤 오픈, 100장" 이벤트를 생성한다 **[Claude 결정]**
- 빌드와 테스트 환경 주의사항(JDK keg-only `JAVA_HOME`, OrbStack `api.version=1.43`)은 기존과 같다
- 작업 브랜치: `feat/coupon-issue` (현재 `feat/board-api`에서 분기할지 `main`에서 분기할지는 구현 시작 시 확인)

---

## 8. 빌더에게 남긴 것
- 테스트 수치(사용자 수, 스레드 풀 크기), 타임아웃과 CB 임계값, TTL
- `DataIntegrityViolationException` 변환 위치와 제약 이름 매칭 방식
- 패키지와 클래스 세부 구조(기존 계층형 `controller/service/repository/entity` 유지)
- Testcontainers Redis 구성 방식, 장애 주입 방식

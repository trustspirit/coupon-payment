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

---

# 학습 로드맵: 한정 쿠폰 발급 → 쿠폰 적용 주문·결제

게시판 API 위에 **동시성 · 멱등성 · 트래픽 방어**를 단계별로 학습하는 두 프로젝트를 얹는다.
각 단계는 "가장 단순한 버전으로 문제를 재현 → 도구를 하나씩 추가해 해결" 순서로 진행한다.

> 상태: 설계 완료, 구현 시작 전

## 1. 한정 수량 쿠폰 발급 (선착순 100장)

### 요구사항

- 이벤트마다 쿠폰 **100장 한정**, **오픈 시각 이전에는 발급 불가**
- 사용자당 이벤트별 **1장**. 이미 받은 사용자가 다시 요청하면 `409 COUPON_ALREADY_ISSUED`
- 10만 명이 오픈 시각에 몰려도 **정확히 100장**만 발급되고, DB와 다른 기능은 무너지지 않아야 함

### 최종 요청 흐름

```
요청 → [JWT 인증] → [Rate Limiter] → [메모리 체크: 오픈 전/매진]
     → [Redis Lua 게이트: 중복·재고 판정] → (최대 100건) → [DB 트랜잭션] → 201
```

- **Redis**: 당첨 판정을 담당하는 문지기. 대부분의 요청을 DB에 닿기 전에 거절한다
- **DB**: 영속성의 최종 진실. `(event_id, user_id)` 유니크 제약과 원자적 재고 차감이 Redis가 틀렸을 때도 막아주는 최종 방어선이다
- Redis 장애 시에는 Circuit Breaker로 `503`을 빠르게 반환한다. DB로 fallback하지 않는다(트래픽이 DB로 쏟아지는 것을 방지)

### 단계

| # | 단계 | 학습 포인트 |
|---|---|---|
| 1 | 순진한 DB 구현 | check-then-act 경쟁 조건, Lost Update 재현 |
| 2 | 유니크 제약 + 원자적 `UPDATE ... WHERE remaining > 0` + 메모리 매진 플래그 | 트랜잭션 내 순서와 핫 로우 락, 비관적/낙관적 락 비교 |
| 3 | Redis Lua 게이트 + 재고 워밍업 + 실패 시 보상 | 원자적 판정, Redis와 DB 정합성 |
| 4 | Redis 기반 Rate Limiter (사용자별) | 분산 환경 한도, 고정 윈도우와 토큰 버킷 비교 |
| 5 | Circuit Breaker (Resilience4j) + 타임아웃 | fail-fast, 상태 전이 |
| 6 | 통계: 결과별 카운터, HyperLogLog 고유 사용자 수, 매진 소요 시간 | DB에 남지 않는 트래픽 측정 |
| 7 | k6 부하 테스트 (선택) | DB 전략과 Redis 전략의 수치 비교 |

### API (예정)

| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|:--:|
| GET | /api/coupon-events/{eventId} | 이벤트 정보 (수량, 오픈 시각) | ✗ |
| POST | /api/coupon-events/{eventId}/issuance | 쿠폰 발급 | ✓ |
| GET | /api/coupon-events/{eventId}/issuance/me | 내 발급 여부 | ✓ |
| GET | /api/coupon-events/{eventId}/stats | 발급 통계 | ✓ |

| 상황 | HTTP | code |
|---|---|---|
| 발급 성공 | 201 | — |
| 이벤트 없음 | 404 | `COUPON_EVENT_NOT_FOUND` |
| 오픈 전 | 409 | `COUPON_EVENT_NOT_OPEN` |
| 이미 발급받음 | 409 | `COUPON_ALREADY_ISSUED` |
| 매진 | 409 | `COUPON_SOLD_OUT` |
| 요청 과다 | 429 | `TOO_MANY_REQUESTS` |
| Redis 장애 | 503 | `COUPON_SERVICE_UNAVAILABLE` |

> Kafka/SQS는 이 프로젝트에서 쓰지 않는다. Redis를 앞에 두면 DB 쓰기가 성공한 100건뿐이라 메시지 큐를 정당화할 이유가 없다.

## 2. 쿠폰 적용 주문·결제 (예정)

### 시나리오

1. 아이템에 쿠폰을 적용해 구매 → 아이템 재고 차감, 쿠폰 사용 처리
2. 결제 진행 → 완료되면 사용자에게 구매 완료 전달
3. 구매 취소 → 쿠폰과 재고 원복, 결제 취소

### 학습 주제

- 외부 결제(PG)는 DB 트랜잭션에 묶을 수 없다 → **Saga 패턴**과 보상 트랜잭션
- 상태 머신: 주문(`CREATED → PAYMENT_PENDING → PAID → CANCELLED`), 쿠폰(`ISSUED → RESERVED → USED`)
- 결제 재시도와 웹훅 중복을 처리하는 `Idempotency-Key`
- Transactional Outbox, 멱등 consumer, 재시도와 DLT
- Kafka/SQS 도입 여부: 서비스 분리 여부와 결제 결과의 비동기 수신 여부에 따라 결정

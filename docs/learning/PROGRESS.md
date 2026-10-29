# 학습 진행 현황 (Learning Progress)

> **새 세션/다른 에이전트**: 저장소 루트의 `AGENTS.md`를 먼저 읽고, 이 파일의 "현재 위치"부터 이어서 진행한다.
> 사용자는 "PROGRESS.md 읽고 이어서 하자"라고만 말해도 된다.
> 각 Step이 끝날 때마다 이 파일을 갱신한다.

## 현재 위치

- **설계**: [커머스 MSA 모노레포 설계](../superpowers/specs/2026-10-29-commerce-msa-design.md) — 빌더 체크 반영 완료, 기본값 8개(설계 문서 Claude 결정 항목)는 **사용자 검토 대기**
- **완료**: Step 0.1 (2026-10-29)
- **다음 할 일**: **Step 0.2** — `gradle/libs.versions.toml` + `build-logic` convention plugin

## 문서

| 문서 | 내용 |
|---|---|
| [커머스 MSA 설계](../superpowers/specs/2026-10-29-commerce-msa-design.md) | **기준 문서**. 전체 구조, 인증, Phase/Step 순서 |
| [쿠폰 발급 설계](../superpowers/specs/2026-10-29-limited-coupon-design.md) | Phase 2~3 세부 (모델, API, 불변식, 학습 질문) |
| [주문·결제 초안](../superpowers/specs/2026-10-29-coupon-order-saga-draft.md) | Phase 4 열린 질문 (Phase 4 전에 브레인스토밍) |

## 작업 방식 (합의됨, 2026-10-29 변경)
- **사용자가 직접 Step 하나씩 구현** → 에이전트는 목표·개념·완료 기준·힌트 제공, 코드 리뷰
- 설계 판단 지점마다 ★ Insight로 설명
- **테스트는 당분간 스킵, 나중에 돌아온다** (Phase 2 전에 재논의). 완료 확인은 빌드 성공 + 수동 확인(curl)
- 빌드: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew build` (Docker 실행 필요)
- 브랜치 없이 `master`에서 작업, 커밋은 사용자가 함 (푸시 전 테스트 통과)
- 설계·계획·진행 문서와 `AGENTS.md`/`CLAUDE.md`는 **git에 커밋** (다른 컴퓨터, 원격 에이전트도 이어받기 위해)

상태 표기: ⬜ 시작 전 · 🔄 진행 중 · 👀 리뷰 대기 · ✅ 완료

---

## Phase 0 — 모노레포 전환
| Step | 내용 | 상태 | 커밋 |
|---|---|---|---|
| 0.1 | 기존 코드 → `examples/board`, 멀티모듈 루트 | ✅ | (이번 커밋) |
| 0.2 | 버전 카탈로그 + `build-logic` convention plugin | ⬜ | |
| 0.3 | docker-compose: 서비스별 DB | ⬜ | |

## Phase 1 — 인증 + Gateway
| Step | 내용 | 상태 | 커밋 |
|---|---|---|---|
| 1.1 | account-service: board 인증 이관 | ⬜ | |
| 1.2 | RS256 + kid + JWKS, refresh 쿠키 Path 축소 | ⬜ | |
| 1.3 | refresh 회전 + 재사용 감지 | ⬜ | |
| 1.4 | security-resource-server 라이브러리 + catalog 뼈대 | ⬜ | |
| 1.5 | gateway: 라우팅, 1차 검증, 쿠키→Bearer, 요청 ID | ⬜ | |
| 1.6 | 통합 확인: 로그인 1회 → Gateway 경유 호출, refresh | ⬜ | |
| 1.7 | (선택) 키 회전 | ⬜ | |

## Phase 2 — 쿠폰 발급 (catalog-service)
> ⚠️ 시작 전에 **테스트 방식을 다시 정한다** (동시성 테스트가 학습의 핵심). 설계 §8 참고

| Step | 내용 | 상태 | 커밋 |
|---|---|---|---|
| 2.1 | 순진한 DB 구현 (race 재현) | ⬜ | |
| 2.2 | 유니크 + 원자적 UPDATE + 메모리 체크 | ⬜ | |
| 2.3 | Redis Lua 게이트 + 워밍업 + 보상 | ⬜ | |

## Phase 3 — 트래픽 방어
| Step | 내용 | 상태 | 커밋 |
|---|---|---|---|
| 3.1 | Gateway rate limit (Bucket4j 토큰 버킷) | ⬜ | |
| 3.2 | Circuit Breaker + 타임아웃 | ⬜ | |
| 3.3 | 통계 | ⬜ | |
| 3.4 | (선택) k6 부하 테스트 | ⬜ | |

## Phase 4 — 주문/결제 Saga
| 단계 | 상태 |
|---|---|
| 브레인스토밍 (주문·결제 초안의 열린 질문 2~8) | ⬜ Phase 3 이후 |
| Step 분할 → 설계 문서 §7에 추가 | ⬜ |

## Phase 5 — Kafka + 이벤트 소비자
| 단계 | 상태 |
|---|---|
| Step 분할 | ⬜ Phase 4 이후 |

---

## 결정 로그

| 날짜 | 결정 | 누가 |
|---|---|---|
| 2026-10-29 | 단계별 진화 방식 (깨뜨려 보고 고치기) | 사용자 |
| 2026-10-29 | `coupon_issue` 유니크 제약 + 같은 트랜잭션에서 재고 차감 | 사용자 |
| 2026-10-29 | 재요청 시 409 "이미 발급받음" | 사용자 |
| 2026-10-29 | 오픈 시각 이전 발급 불가, 재고는 DB에 저장 | 사용자 |
| 2026-10-29 | 통계(결과별 카운터, 고유 사용자 수)를 마지막 단계로 포함 | 사용자 |
| 2026-10-29 | Redis 장애 시 DB fallback 없이 503 fail-fast | Claude (변경 가능) |
| 2026-10-29 | INSERT 먼저 → UPDATE 순서, db/redis 전략 공존 | Claude (변경 가능) |
| 2026-10-29 | 커머스 구조: 주문(오케스트레이터) / 결제(PG 래핑) / 상품·재고·쿠폰 / 가짜 PG, 동기 경로 + Kafka 비동기 경로 | 사용자 |
| 2026-10-29 | Gradle 멀티모듈 모노레포, 서비스별 Spring Boot 앱 + 서비스별 DB | 사용자 |
| 2026-10-29 | 기존 board는 예제 코드로 보존 (`examples/board`) | 사용자 |
| 2026-10-29 | 인증/인가는 별도 `account-service` | 사용자 |
| 2026-10-29 | API Gateway를 Phase 1부터 포함 (한 번 로그인, 단일 출처) | 사용자 |
| 2026-10-29 | 사용자가 직접 Step별 구현, 에이전트는 보조 | 사용자 |
| 2026-10-29 | 쿠폰 발급 → catalog-service (Phase 2~3) | 사용자 |
| 2026-10-29 | RS256 + JWKS, Gateway 1차 검증 + 서비스 재검증, 쿠키→Bearer 변환, 사용자 토큰 전달 | Claude (변경 가능) |
| 2026-10-29 | Spring Cloud Gateway **Server MVC**, rate limit은 Bucket4j 토큰 버킷 (고정 윈도우는 선택 비교) | Claude (변경 가능) |
| 2026-10-29 | Postgres 1개 + 서비스별 DB/사용자 | Claude (변경 가능) |
| 2026-10-29 | Gateway는 Server MVC로 확정 | 사용자 |
| 2026-10-29 | refresh 회전 + 재사용 감지를 Step 1.3으로 추가 (board에는 없던 기능) | 사용자 |
| 2026-10-29 | 테스트는 스킵하고 나중에 돌아옴 (Phase 2 전 재논의) | 사용자 |
| 2026-10-29 | 브랜치 없이 master에서 작업, 커밋은 사용자가 함 | 사용자 |
| 2026-10-29 | 인수인계 문서(AGENTS.md, CLAUDE.md, PROGRESS.md, 설계 문서)를 git에 포함 | 사용자 |

## 학습 노트

> Step별 측정 결과, 학습 질문에 대한 답, 새로 알게 된 점을 기록한다.

### Step 0.1
- `settings.gradle`: `rootProject.name`은 모노레포 이름, `include ':examples:board'` → 디렉터리 `/` 대신 `:`. 중간 `:examples`는 빈 프로젝트로 자동 생성됨
- 루트에 `build.gradle`을 남기면 루트가 "소스 없는 Boot 앱"이 됨 → 앱 설정은 모듈로
- `.gradle/`(빌드 전체 캐시, 루트에 하나)과 `build/`(모듈마다 생성)는 생성물 → 옮기지 말고 지우면 Gradle이 다시 만듦
- `.gitignore`의 `build/`(앞에 `/` 없음)는 모든 깊이에 매칭됨 → 모듈별로 다시 쓸 필요 없음
- IntelliJ Move 리팩터링이 문서 속 경로 문자열까지 바꿀 수 있음 → 파일 이동은 `git mv`로
- `git mv`만 한 커밋은 rename(`R`)으로 인식 → `git log --follow`로 이력 추적 가능

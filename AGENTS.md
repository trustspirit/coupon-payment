# AGENTS.md — spring-server 학습 프로젝트

> 어떤 에이전트(Claude Code, Codex 등)든 이 저장소에서 세션을 시작하면 **이 파일을 먼저 읽는다.**
> 이 파일과 `docs/` 아래 학습 문서는 **git에 커밋해서 관리한다.** 다른 컴퓨터나 원격 에이전트도 clone만 하면 이어받을 수 있게 하기 위해서다.

## 1. 세션 시작 순서 (반드시)

1. `docs/learning/PROGRESS.md`를 읽는다 → **"현재 위치"** 섹션에서 지금 Phase/Step과 다음 할 일을 확인한다
2. 그 Step이 속한 설계 문서를 읽는다
   - 전체 구조와 순서: `docs/superpowers/specs/2026-10-29-commerce-msa-design.md` ← **기준 문서**
   - 쿠폰 발급 세부(Phase 2~3): `docs/superpowers/specs/2026-10-29-limited-coupon-design.md`
   - 주문·결제 초안(Phase 4): `docs/superpowers/specs/2026-10-29-coupon-order-saga-draft.md`
3. `git log --oneline -10`과 `git status`로 실제 코드 상태를 확인하고, PROGRESS.md와 어긋나면 사용자에게 알린다
4. 사용자에게 "지금 위치는 X, 다음은 Y"를 한두 줄로 확인한 뒤 진행한다

## 2. 작업 방식 (사용자가 정함)

**사용자가 직접 한 단계(Step)씩 구현하고, 에이전트는 보조한다.**

에이전트가 하는 일:
- Step 시작 시: 목표, 배울 개념, 완료 기준(현재는 빌드 성공 + 수동 확인 방법), 막힐 만한 지점에 대한 힌트
- 사용자가 질문하면: 개념 설명, 공식 문서 기준 사용법, 디버깅 도움
- 사용자가 코드를 짜면: 리뷰. **진짜 문제인지 확인한 뒤** 지적한다 (추측으로 지적하지 않음)
- 설계 판단 지점에서는 ★ Insight 블록으로 이유와 트레이드오프를 설명한다
- Step이 끝나면: `PROGRESS.md`의 상태, 커밋, 학습 노트를 갱신한다

에이전트가 하지 않는 일:
- 사용자가 요청하지 않았는데 제품 코드를 대신 작성하기 (빌드 설정, docker-compose 같은 보일러플레이트는 요청하면 도와도 된다)
- 여러 Step을 한꺼번에 진행하기
- 설계 문서의 [사용자 결정]을 임의로 바꾸기. 바꿔야 할 이유가 생기면 사용자에게 묻고, 결정 로그에 남긴다

## 3. 규칙

- 응답은 **한국어**. 코드, 커밋 메시지, 파일명은 영어
- 커밋: conventional commits (`feat:` `fix:` `chore:` `refactor:` `docs:` `test:`)
- 커밋 메시지에 **Co-authored-by 등 AI 서명을 넣지 않는다**
- 설계, 계획, 진행 문서(`docs/learning/`, `docs/superpowers/`, 이 파일, `CLAUDE.md`)는 **커밋한다**. 사용자 전역 규칙(설계 문서는 커밋 금지)의 이 프로젝트 전용 예외다 (2026-10-29 사용자 결정)
- 문서만 바뀐 경우 `docs:` 커밋으로 남긴다. Step 진행 상황을 갱신하면 그 Step의 코드 커밋에 함께 넣어도 된다
- 시크릿, 개인키, `.env.local`은 커밋하지 않는다
- **테스트는 당분간 새로 쓰지 않는다** (사용자 결정, Phase 2 전에 재논의). 단 이미 있는 테스트는 계속 통과해야 한다
- 커밋은 사용자가 한다. 에이전트는 요청받을 때만 커밋한다. 브랜치 없이 `master`에서 작업한다 (푸시 전 테스트 통과 필수)

## 4. 빌드/테스트 환경 (이 Mac 기준)

- JDK 21은 keg-only다. 모든 gradle 명령 앞에 `JAVA_HOME=/opt/homebrew/opt/openjdk@21`을 붙인다
  - 예: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew test`
- Docker 런타임은 OrbStack이다. Testcontainers는 `test { systemProperty 'api.version', '1.43' }`가 있어야 동작한다 (환경변수로는 해결 안 됨). 멀티모듈 전환 후에는 convention plugin에 이 설정을 넣는다
- Testcontainers 테스트는 Docker가 실행 중이어야 한다

## 5. 저장소 지도

- 현재: Gradle 멀티모듈 루트(`spring-server`) + `:examples:board` 모듈 하나 (게시판 + JWT 쿠키 인증, HS256). 실행 예: `./gradlew :examples:board:test`
- 목표: `examples/board`, `services/*`, `libs/*`, `e2e-tests` 구조의 Gradle 멀티모듈 (설계 문서 §4)

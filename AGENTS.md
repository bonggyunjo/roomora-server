---
description: Roomora 백엔드(roomora-server) AI 에이전트 루트 행동 강령 (SSOT).
layer: L0
---

# AGENTS.md — Roomora Server

## 1. 프로젝트 개요
- **서비스**: Roomora (조건 기반 인테리어 레시피 · 주간 콘테스트 · AI RAG 통합 검색 플랫폼)
- **역할**: Spring Boot 3.x 기반 REST API, JWT/OAuth2 인증, Redis ZSET 랭킹, `pgvector` Hybrid RAG 파이프라인

## 2. 기술 스택 및 버전
- Java 17+, Spring Boot 3.3.x, Gradle
- PostgreSQL 16 (`pgvector`), Redis, Spring AI, Resilience4j

## 3. 기준 문서 우선순위 & 라우팅 지도
- **우선순위**: `DEC(decisions.md) > L1(system_architecture.md / erd_schema.md) > L2(*_spec.md)`
- **외부 옵시디언 볼트**: `../dev-obsidian-base/10_Projects/aesthetics-of-space-v2/`
  - 세션 시작 시: `activeContext.md` + `00_MOC/project_moc.md` 먼저 열람
  - 기능 구현 시: `02_Specs_and_Skills/<domain>_spec.md` 열람 후 `plan.md` 작성
  - 플레이북: `../dev-obsidian-base/30_Agent_Playbooks/` (`gate1_plan_auditor.md`, `gate2_code_reviewer.md`)

## 4. 빌드·테스트 명령 (Narrowest Verification)
- 전체 빌드 대신 수정 도메인 테스트만 가장 좁게 실행한다:
  - `./gradlew test --tests "com.roomora.server.<domain>.*"`
- 최종 검증 스크립트: `pwsh -File scripts/verify.ps1` (`Exit Code 0` 확인)

## 5. 코드 스타일 & 보안 원칙 (Good / Bad)
- 인증 사용자 식별 시 헤더/파라미터를 신뢰하지 않는다:
  - Good: `@AuthenticationPrincipal CustomUserDetails user`
  - Bad: `@RequestHeader("userId") Long userId`
- AI 임베딩/LLM 호출은 DB 트랜잭션 안에 동기 블로킹으로 넣지 않는다:
  - Good: `@TransactionalEventListener(phase = AFTER_COMMIT)` + 비동기 워커 + Resilience4j Fallback
  - Bad: `save()` 메서드 내부에서 외부 AI API 동기 호출

## 6. 금지 구역 (Do Not Touch)
- 이미 적용된 Flyway 마이그레이션(`V1__*.sql` 등), `.env`, 운영 시크릿 설정

## 7. 절대 금지 규칙
- 추측성 구현 금지, 테스트 임의 삭제 금지, 무단 의존성 추가 금지, 로컬 절대경로(`C:/temp/...`) 하드코딩 금지

## 8. 미결정 사항 (Ask Before Coding)
- DB/임베딩 모델 공급자 변경 또는 콘테스트 1일/회차 투표 정책 변경 시 구현 전 사용자에게 확인한다.

## 9. 브랜치 · 커밋 규칙
- 브랜치: `feat/<domain>-<task>`, `fix/<domain>-<bug>`
- 커밋: Conventional Commits (예: `feat: 레시피 조건 필터 및 Keyset 페이징 구현`)

## 10. 완료 기준 (Definition of Done)
- 스펙(`REQ-nnn`) 충족 + 단위/동시성 테스트 통과(`Exit Code 0`) + `activeContext.md`(50줄 이내 덮어쓰기) 및 L2/L3 문서 동기화

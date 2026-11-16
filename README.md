# Roomora Server (`roomora-server`)

> **Roomora (루모라)** — 조건 기반 인테리어 레시피 · 주간 콘테스트 · AI RAG 통합 검색 플랫폼의 백엔드 서버입니다.  
> 학부 캡스톤 레거시(`The-Aesthetics-Of-Space-Project`)의 보안·성능·인프라 결함을 진단하고 전면 재설계한 v2 프로젝트입니다.

## 기술 스택
- **Language & Framework**: Java 17+, Spring Boot 3.x, Spring Security (JWT + OAuth2 Kakao/Google)
- **Data & Vector**: PostgreSQL 16 + `pgvector` (단일 DB Hybrid Search), Spring Data JPA, QueryDSL
- **Cache & Concurrency**: Redis (Refresh Token, 콘테스트 ZSET 실시간 랭킹, RAG 질의 캐싱, Rate Limit)
- **AI & Resilience**: Spring AI, `@TransactionalEventListener(AFTER_COMMIT)` 비동기 임베딩, Resilience4j (Circuit Breaker + Fallback)
- **Storage & Test**: AWS S3 / Cloudflare R2 (Presigned URL), JUnit 5, k6

## 문서 & 하네스 연동
- 공용 설계 문서(옵시디언): `../dev-obsidian-base/10_Projects/aesthetics-of-space-v2/`
- 에이전트 작업 규칙: [AGENTS.md](./AGENTS.md)

---
applyTo: "src/main/java/**/rag/**"
---

# RAG 도메인 작업 규칙

- 구현 전 반드시 `../dev-obsidian-base/10_Projects/roomora/02_Specs_and_Skills/rag_search_spec.md`를 먼저 읽는다.
- 외부 LLM/임베딩 API 호출은 절대 `@Transactional` 내부에서 동기 실행하지 않는다 (`indexPayloadOutsideTx` / `AFTER_COMMIT` 유지).
- AI 장애·타임아웃 시에도 검색 API는 `500` 대신 `Resilience4j` Fallback(`200 OK`, `fallback: true`)으로 우회하고, 임베딩 클라이언트는 결정적 로컬 벡터로 폴백해야 한다.
- 프롬프트에 게시글 본문을 넣을 때는 `<document>` 태그로 격리해 Prompt Injection을 차단한다.

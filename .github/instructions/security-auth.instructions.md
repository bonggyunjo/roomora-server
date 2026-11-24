---
applyTo: "src/main/java/**/auth/**,src/main/java/**/global/security/**"
---

# Auth & Security 도메인 작업 규칙

- 구현 전 반드시 `../dev-obsidian-base/10_Projects/roomora/02_Specs_and_Skills/auth_oauth_spec.md`를 먼저 읽는다.
- 인증 사용자 식별은 반드시 `@AuthenticationPrincipal CustomUserDetails`를 사용하며, `@RequestHeader("userId")` 등 클라이언트 전달 ID를 신뢰하지 않는다.
- Refresh Token 재발급 시 기존 토큰을 즉시 폐기(RT Rotation)하고, 재사용 탐지 시 해당 사용자의 Redis 세션을 전체 무효화한다.
- `.env` 및 실제 OAuth2/AI 시크릿 값은 절대 코드·로그·문서에 커밋하지 않는다.

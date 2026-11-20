package com.roomora.server.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 오류가 발생했습니다."),

    // Auth & User
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "A001", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "A002", "이미 사용 중인 닉네임입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A003", "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A004", "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A005", "유효하지 않거나 만료된 토큰입니다."),
    REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "A006", "비정상적인 토큰 재사용이 감지되어 모든 세션이 만료되었습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A007", "접근 권한이 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "A008", "사용자를 찾을 수 없습니다."),
    SOCIAL_USER_PASSWORD_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "A009", "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다."),

    // Recipe & Community
    RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "레시피를 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "R002", "댓글을 찾을 수 없습니다."),
    INVALID_COMMENT_DEPTH(HttpStatus.BAD_REQUEST, "R003", "대댓글에는 답글을 작성할 수 없습니다."),
    INVALID_UPLOAD_REQUEST(HttpStatus.BAD_REQUEST, "R004", "허용되지 않는 파일 형식입니다."),

    // Contest & Ranking
    CONTEST_ROUND_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "콘테스트 회차를 찾을 수 없습니다."),
    CONTEST_ROUND_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "N002", "진행 중인 콘테스트 회차가 아닙니다."),
    CONTEST_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "N003", "콘테스트 출품작을 찾을 수 없습니다."),
    DUPLICATE_CONTEST_SUBMISSION(HttpStatus.CONFLICT, "N004", "해당 회차에 이미 출품작을 등록했습니다."),
    ALREADY_VOTED_IN_ROUND(HttpStatus.CONFLICT, "N005", "이번 회차에 이미 투표했습니다."),
    SELF_VOTE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "N006", "본인 출품작에는 투표할 수 없습니다."),

    // AI RAG & Checklist
    GUIDE_NOT_FOUND(HttpStatus.NOT_FOUND, "G001", "인테리어 가이드를 찾을 수 없습니다."),
    RAG_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "G002", "일일 AI 검색 횟수를 초과했습니다. 일반 조건 검색(/api/recipes)을 이용해 주세요."),
    CHECKLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "K001", "체크리스트를 찾을 수 없습니다."),
    CHECKLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "K002", "체크리스트 항목을 찾을 수 없습니다."),

    // MyPage & Social
    SELF_FOLLOW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "U001", "자기 자신을 팔로우할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

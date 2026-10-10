package com.gdghost.common.error;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * API 설계서(노션) 에러 코드 DB와 1:1. 코드 이름은 프론트와의 계약이라 바꾸지 않는다.
 */
public enum ErrorCode {

    // 공통
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값 오류"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인 필요"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한 없음"),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "CSRF 토큰 오류"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상 없음"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "중복 요청 키 재사용"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청 횟수 초과"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류"),

    // 인증
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인 실패"),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 잠김"),
    LOGIN_ID_TAKEN(HttpStatus.CONFLICT, "이미 쓰는 아이디"),
    NICKNAME_TAKEN(HttpStatus.CONFLICT, "이미 쓰는 닉네임"),
    NICKNAME_RESERVED(HttpStatus.BAD_REQUEST, "쓸 수 없는 닉네임"),
    EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "이미 가입된 이메일"),
    EMAIL_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘 인증 메일 한도 초과"),
    EMAIL_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "인증 메일 재발송 대기"),
    PASSWORD_POLICY(HttpStatus.BAD_REQUEST, "비밀번호 규칙 위반"),
    INVITE_CODE_INVALID(HttpStatus.BAD_REQUEST, "쓸 수 없는 초대 코드"),
    INVITE_CODE_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "초대 코드 입력 잠김"),
    VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증번호 불일치"),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호 만료"),
    SIGNUP_SESSION_EXPIRED(HttpStatus.BAD_REQUEST, "가입 시간 만료"),
    RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "확인 링크 만료"),
    MAIL_QUOTA_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE, "메일 발송 한도 도달"),

    // 계정 · 운영
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "이용 정지된 계정"),
    CURRENT_PASSWORD_WRONG(HttpStatus.BAD_REQUEST, "현재 비밀번호 불일치"),
    EMAIL_CHANGE_TOO_SOON(HttpStatus.CONFLICT, "이메일 변경 대기 기간"),
    NICKNAME_CHANGE_TOO_SOON(HttpStatus.CONFLICT, "닉네임 변경 대기 기간"),
    LAST_STAFF(HttpStatus.CONFLICT, "마지막 운영진"),
    TARGET_IS_STAFF(HttpStatus.CONFLICT, "운영진은 정지 불가"),

    // 질문 · 라우팅
    NOT_AUTHOR(HttpStatus.FORBIDDEN, "작성자가 아님"),
    OWN_QUESTION_ANSWER(HttpStatus.CONFLICT, "본인 질문에 답변 불가"),
    ADOPT_INVALID(HttpStatus.BAD_REQUEST, "채택할 수 없는 답변"),
    ANONYMOUS_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "익명 불가 질문"),
    ANSWER_TOO_FAST(HttpStatus.TOO_MANY_REQUESTS, "연속 제출"),
    ROUTING_NOT_ASSIGNED(HttpStatus.FORBIDDEN, "지목되지 않은 질문"),

    // 신고 · 공지
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고함"),
    REPORT_ALREADY_HANDLED(HttpStatus.CONFLICT, "이미 처리된 신고"),
    NOTICE_REMIND_USED(HttpStatus.CONFLICT, "재알림 이미 보냄"),

    // 모집 · 이벤트
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 모집"),
    RECRUIT_CLOSED(HttpStatus.CONFLICT, "마감된 모집"),
    RECRUIT_FULL(HttpStatus.CONFLICT, "정원 마감"),
    RECRUIT_OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT, "개설자는 참여 취소 불가"),
    RECRUIT_TIME_PAST(HttpStatus.BAD_REQUEST, "지난 시각"),
    EVENT_CLOSED(HttpStatus.CONFLICT, "끝난 이벤트"),
    CERT_ALREADY_REVIEWED(HttpStatus.CONFLICT, "이미 검토된 인증"),
    CERT_NOT_REJECTED(HttpStatus.CONFLICT, "반려되지 않은 인증"),

    // 업로드
    UPLOAD_NOT_FOUND(HttpStatus.BAD_REQUEST, "업로드 파일 없음"),
    UPLOAD_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 크기 초과"),
    UPLOAD_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식"),

    // 커피챗
    COFFEE_DISABLED(HttpStatus.CONFLICT, "커피챗 받지 않음"),
    COFFEE_PENDING_EXISTS(HttpStatus.CONFLICT, "응답 대기 중인 신청 있음"),
    COFFEE_COOLDOWN(HttpStatus.CONFLICT, "다시 신청 대기 기간"),
    COFFEE_ALREADY_RESPONDED(HttpStatus.CONFLICT, "이미 응답한 신청");

    // 실제 도메인이 정해지면 바꾼다. 주소는 바뀌지 않는 문서 주소여야 함
    private static final String TYPE_BASE = "https://gdghost.dev/errors/";

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public URI type() {
        return URI.create(TYPE_BASE + name().toLowerCase().replace('_', '-'));
    }

    /** detail이 없으면 title을 쓴다 */
    public ProblemDetail toProblem(String detail, List<ErrorField> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail != null ? detail : title);
        applyTo(problem);
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    public void applyTo(ProblemDetail problem) {
        problem.setType(type());
        problem.setTitle(title);
        problem.setProperty("code", name());
    }

    /**
     * Spring MVC가 직접 만드는 에러(없는 주소 · 본문 해석 실패 · 405 등)에 붙일 코드.
     * 상태 코드는 그대로 두고, 에러 코드 DB에 맞는 것이 없는 4xx는 VALIDATION_FAILED로 묶는다.
     */
    public static ErrorCode fromStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 429 -> RATE_LIMITED;
            default -> status.is5xxServerError() ? INTERNAL_ERROR : VALIDATION_FAILED;
        };
    }

}

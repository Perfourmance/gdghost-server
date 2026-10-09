-- GDGhost 초기 스키마 — ERD 1–5묶음, 테이블 38개
-- 규칙: PK bigint 자동 증가 · datetime은 UTC · date는 한국 날짜 그대로 · enum은 varchar(영문 코드)
-- 서로 참조하는 FK(member ↔ invite_code, question ↔ answer)는 맨 아래에서 ALTER로 건다

-- =========================================================
-- 1 · 계정 · 프로필
-- =========================================================

CREATE TABLE member (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    login_id               VARCHAR(16)  NULL,
    password_hash          VARCHAR(100) NOT NULL,
    email                  VARCHAR(254) NULL,
    name                   VARCHAR(30)  NULL,
    nickname               VARCHAR(12)  NULL,
    generation             TINYINT      NOT NULL,
    invite_code_id         BIGINT       NULL,
    student_no             CHAR(2)      NULL,
    department             VARCHAR(50)  NULL,
    position               VARCHAR(20)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    bio                    VARCHAR(120) NULL,
    status_note            VARCHAR(60)  NULL,
    status_note_updated_at DATETIME(6)  NULL,
    role                   VARCHAR(10)  NOT NULL DEFAULT 'MEMBER',
    staff_title            VARCHAR(30)  NULL,
    accept_question        BOOLEAN      NOT NULL DEFAULT TRUE,
    accept_coffee          BOOLEAN      NOT NULL DEFAULT FALSE,
    coffee_memo            VARCHAR(100) NULL,
    push_on                BOOLEAN      NOT NULL DEFAULT FALSE,
    hidden_from_list       BOOLEAN      NOT NULL DEFAULT FALSE,
    password_changed_at    DATETIME(6)  NULL,
    nickname_changed_at    DATETIME(6)  NULL,
    email_changed_at       DATETIME(6)  NULL,
    last_seen_at           DATETIME(6)  NULL,
    created_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)  NULL,
    withdrawn_at           DATETIME(6)  NULL,
    suspended_at           DATETIME(6)  NULL,
    suspended_reason       VARCHAR(200) NULL,
    suspended_by           BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_login_id (login_id),
    UNIQUE KEY uk_member_email (email),
    UNIQUE KEY uk_member_nickname (nickname),
    CONSTRAINT fk_member_suspended_by FOREIGN KEY (suspended_by) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE invite_code (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20) NOT NULL,
    generation  TINYINT     NOT NULL,
    created_by  BIGINT      NULL,             -- 준비용 코드는 NULL
    max_uses    INT         NOT NULL,
    used_count  INT         NOT NULL DEFAULT 0,
    expires_at  DATETIME(6) NOT NULL,
    revoked_at  DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_invite_code_code (code),
    CONSTRAINT fk_invite_code_created_by FOREIGN KEY (created_by) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tag (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    kind       VARCHAR(20) NOT NULL,          -- INTEREST SKILL EXPERIENCE
    parent_id  BIGINT      NULL,              -- SKILL이면 관심 분야
    parent_key BIGINT AS (IFNULL(parent_id, 0)) STORED,
    name       VARCHAR(30) NOT NULL,
    group_name VARCHAR(30) NULL,              -- 경험 태그 묶음
    sort_order INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_kind_parent_name (kind, parent_key, name),
    CONSTRAINT fk_tag_parent FOREIGN KEY (parent_id) REFERENCES tag (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE member_tag (
    member_id BIGINT NOT NULL,
    tag_id    BIGINT NOT NULL,
    PRIMARY KEY (member_id, tag_id),
    CONSTRAINT fk_member_tag_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_member_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE member_contact (
    member_id BIGINT       NOT NULL,
    type      VARCHAR(20)  NOT NULL,          -- LINKEDIN INSTA KAKAO PHONE
    value     VARCHAR(200) NOT NULL,
    is_public BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (member_id, type),
    CONSTRAINT fk_member_contact_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE email_verification (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(254) NULL,          -- 탈퇴 시 비움
    member_id     BIGINT       NULL,          -- 가입 때는 NULL
    code_hash     VARCHAR(100) NOT NULL,
    purpose       VARCHAR(20)  NOT NULL,      -- SIGNUP RESET EMAIL_CHANGE
    attempt_count TINYINT      NOT NULL DEFAULT 0,
    expires_at    DATETIME(6)  NOT NULL,
    verified_at   DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_email_verification_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE login_token (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    member_id    BIGINT       NOT NULL,
    token_hash   CHAR(64)     NOT NULL,       -- SHA-256
    remember     BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at   DATETIME(6)  NOT NULL,
    last_used_at DATETIME(6)  NOT NULL,
    user_agent   VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_login_token_hash (token_hash),
    CONSTRAINT fk_login_token_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE push_subscription (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    member_id     BIGINT       NOT NULL,
    endpoint      TEXT         NOT NULL,
    endpoint_hash CHAR(64)     NOT NULL,      -- SHA-256, endpoint가 길어서 해시에 UK
    p256dh        VARCHAR(255) NOT NULL,
    auth          VARCHAR(255) NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_push_subscription_endpoint_hash (endpoint_hash),
    CONSTRAINT fk_push_subscription_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE email_change_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    member_id       BIGINT       NOT NULL,
    old_email       VARCHAR(254) NULL,        -- 탈퇴 시 비움
    new_email       VARCHAR(254) NULL,
    changed_by_type VARCHAR(10)  NOT NULL,    -- SELF STAFF
    changed_by      BIGINT       NOT NULL,
    verified_at     DATETIME(6)  NULL,        -- 새 메일 인증 = 반영 시각
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_email_change_log_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_email_change_log_changed_by FOREIGN KEY (changed_by) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ops_audit_log (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    actor_id         BIGINT      NOT NULL,
    action           VARCHAR(40) NOT NULL,    -- EMAIL_VIEW SUSPEND STAFF_ASSIGN 등
    target_member_id BIGINT      NULL,
    target_type      VARCHAR(30) NULL,        -- INVITE_CODE REPORT CERTIFICATION 등
    target_id        BIGINT      NULL,
    detail           JSON        NULL,
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_ops_audit_log_actor FOREIGN KEY (actor_id) REFERENCES member (id),
    CONSTRAINT fk_ops_audit_log_target_member FOREIGN KEY (target_member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =========================================================
-- 2 · 질문 · 답변 · 라우팅
-- =========================================================

CREATE TABLE question (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    author_id           BIGINT       NOT NULL,
    category            VARCHAR(20)  NOT NULL,    -- JOB CAREER PATH CAMPUS STUDY
    title               VARCHAR(200) NOT NULL,
    body                TEXT         NULL,
    is_anonymous        BOOLEAN      NOT NULL DEFAULT FALSE,
    state               VARCHAR(20)  NOT NULL DEFAULT 'WAITING', -- WAITING ANSWERED ADOPTED
    adopted_answer_id   BIGINT       NULL,
    edited_after_answer BOOLEAN      NOT NULL DEFAULT FALSE,
    view_count          INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NULL,
    deleted_at          DATETIME(6)  NULL,
    deleted_by          VARCHAR(10)  NULL,        -- AUTHOR STAFF
    PRIMARY KEY (id),
    CONSTRAINT fk_question_author FOREIGN KEY (author_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE answer (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    question_id  BIGINT      NOT NULL,
    parent_id    BIGINT      NULL,            -- NULL이면 답변, 있으면 답글
    depth        TINYINT     NOT NULL DEFAULT 0, -- 0~3
    author_id    BIGINT      NOT NULL,
    body         TEXT        NOT NULL,
    edited_at    DATETIME(6) NULL,
    is_anonymous BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at   DATETIME(6) NULL,
    deleted_by   VARCHAR(10) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES question (id),
    CONSTRAINT fk_answer_parent FOREIGN KEY (parent_id) REFERENCES answer (id),
    CONSTRAINT fk_answer_author FOREIGN KEY (author_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE question_anon_alias (
    question_id BIGINT NOT NULL,
    member_id   BIGINT NOT NULL,
    alias_no    INT    NOT NULL,              -- 0 = 글쓴이, 1부터
    PRIMARY KEY (question_id, member_id),
    UNIQUE KEY uk_question_anon_alias_no (question_id, alias_no),
    CONSTRAINT fk_question_anon_alias_question FOREIGN KEY (question_id) REFERENCES question (id),
    CONSTRAINT fk_question_anon_alias_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE routing (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    question_id  BIGINT        NOT NULL,
    member_id    BIGINT        NOT NULL,
    stage        VARCHAR(10)   NOT NULL,      -- STAFF MEMBER DIRECT
    rank_no      TINYINT       NULL,
    score        DECIMAL(6, 4) NULL,
    score_detail JSON          NULL,
    state        VARCHAR(20)   NOT NULL DEFAULT 'CANDIDATE', -- CANDIDATE SENT ANSWERED PASSED CANCELED
    sent_at      DATETIME(6)   NULL,
    responded_at DATETIME(6)   NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_routing_question FOREIGN KEY (question_id) REFERENCES question (id),
    CONSTRAINT fk_routing_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE question_embedding (
    question_id BIGINT      NOT NULL,
    vector      JSON        NOT NULL,         -- 1024차원
    model       VARCHAR(50) NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (question_id),
    CONSTRAINT fk_question_embedding_question FOREIGN KEY (question_id) REFERENCES question (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE member_embedding (
    member_id   BIGINT      NOT NULL,
    vector      JSON        NOT NULL,
    model       VARCHAR(50) NOT NULL,
    source_hash CHAR(64)    NOT NULL,         -- 조립한 글의 SHA-256
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (member_id),
    CONSTRAINT fk_member_embedding_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE coffee_chat (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    requester_id BIGINT       NOT NULL,
    receiver_id  BIGINT       NOT NULL,
    purpose      VARCHAR(200) NOT NULL,
    state        VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED', -- REQUESTED ACCEPTED DECLINED EXPIRED
    responded_at DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_coffee_chat_requester FOREIGN KEY (requester_id) REFERENCES member (id),
    CONSTRAINT fk_coffee_chat_receiver FOREIGN KEY (receiver_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE report (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT       NOT NULL,
    target_type VARCHAR(20)  NOT NULL,        -- QUESTION ANSWER SHARE SHARE_NOTE FEED FEED_COMMENT NOTICE_COMMENT
    target_id   BIGINT       NOT NULL,
    reason      VARCHAR(200) NOT NULL,
    state       VARCHAR(20)  NOT NULL DEFAULT 'OPEN', -- OPEN DISMISSED HIDDEN RESTORED
    handled_by  BIGINT       NULL,
    handled_at  DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_reporter_target (reporter_id, target_type, target_id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES member (id),
    CONSTRAINT fk_report_handled_by FOREIGN KEY (handled_by) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =========================================================
-- 3 · 공지 · 일정 · 모집 · 이벤트
-- =========================================================

CREATE TABLE notice (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    author_id    BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         TEXT         NOT NULL,
    is_pinned    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_must_read BOOLEAN      NOT NULL DEFAULT FALSE,
    due_date     DATE         NULL,
    reminded_at  DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NULL,
    deleted_at   DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notice_author FOREIGN KEY (author_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE notice_read (
    notice_id    BIGINT      NOT NULL,
    member_id    BIGINT      NOT NULL,
    read_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    confirmed_at DATETIME(6) NULL,
    PRIMARY KEY (notice_id, member_id),
    CONSTRAINT fk_notice_read_notice FOREIGN KEY (notice_id) REFERENCES notice (id),
    CONSTRAINT fk_notice_read_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE notice_comment (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    notice_id  BIGINT      NOT NULL,
    author_id  BIGINT      NOT NULL,
    body       TEXT        NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(10) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notice_comment_notice FOREIGN KEY (notice_id) REFERENCES notice (id),
    CONSTRAINT fk_notice_comment_author FOREIGN KEY (author_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE schedule (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    author_id   BIGINT       NOT NULL,
    kind        VARCHAR(20)  NOT NULL,        -- CLUB CONTEST JOB ACADEMIC
    is_official BOOLEAN      NOT NULL DEFAULT FALSE,
    title       VARCHAR(200) NOT NULL,
    start_date  DATE         NOT NULL,        -- 한국 날짜 그대로
    end_date    DATE         NULL,
    place       VARCHAR(100) NULL,
    memo        TEXT         NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NULL,
    deleted_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_author FOREIGN KEY (author_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE recruit (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    author_id    BIGINT       NOT NULL,
    schedule_id  BIGINT       NULL,
    kind         VARCHAR(20)  NOT NULL,       -- TAXI CONTEST SUBSCRIPTION ETC
    title        VARCHAR(200) NOT NULL,
    meet_at      DATETIME(6)  NOT NULL,
    capacity     INT          NOT NULL,
    joined_count INT          NOT NULL DEFAULT 0,
    state        VARCHAR(10)  NOT NULL DEFAULT 'OPEN', -- OPEN CLOSED
    memo         TEXT         NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_recruit_author FOREIGN KEY (author_id) REFERENCES member (id),
    CONSTRAINT fk_recruit_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE recruit_member (
    recruit_id BIGINT      NOT NULL,
    member_id  BIGINT      NOT NULL,
    joined_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (recruit_id, member_id),
    CONSTRAINT fk_recruit_member_recruit FOREIGN KEY (recruit_id) REFERENCES recruit (id),
    CONSTRAINT fk_recruit_member_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE event (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    created_by  BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    description TEXT         NULL,
    requirement VARCHAR(200) NULL,
    guide_steps JSON         NULL,            -- 4단계 안내
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_event_created_by FOREIGN KEY (created_by) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE certification (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    event_id      BIGINT       NOT NULL,
    member_id     BIGINT       NOT NULL,
    body          VARCHAR(300) NULL,
    state         VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING APPROVED REJECTED
    reject_reason VARCHAR(200) NULL,
    reviewed_by   BIGINT       NULL,
    reviewed_at   DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_certification_event FOREIGN KEY (event_id) REFERENCES event (id),
    CONSTRAINT fk_certification_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_certification_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =========================================================
-- 4 · 공유 · 피드 · 사진 · 알림
-- =========================================================

CREATE TABLE share (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    author_id  BIGINT       NOT NULL,
    category   VARCHAR(20)  NOT NULL,         -- CAREER_PREP FOOD PLACE TIP EXTRA LECTURE
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,
    save_count INT          NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NULL,
    deleted_at DATETIME(6)  NULL,
    deleted_by VARCHAR(10)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_share_author FOREIGN KEY (author_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE share_tag (
    share_id BIGINT      NOT NULL,
    name     VARCHAR(20) NOT NULL,
    PRIMARY KEY (share_id, name),
    CONSTRAINT fk_share_tag_share FOREIGN KEY (share_id) REFERENCES share (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE share_save (
    share_id   BIGINT      NOT NULL,
    member_id  BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (share_id, member_id),
    CONSTRAINT fk_share_save_share FOREIGN KEY (share_id) REFERENCES share (id),
    CONSTRAINT fk_share_save_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE share_note (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    share_id   BIGINT       NOT NULL,
    author_id  BIGINT       NOT NULL,
    body       VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6)  NULL,
    deleted_by VARCHAR(10)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_share_note_share FOREIGN KEY (share_id) REFERENCES share (id),
    CONSTRAINT fk_share_note_author FOREIGN KEY (author_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE feed_post (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    author_id  BIGINT      NOT NULL,
    category   VARCHAR(20) NOT NULL,          -- DAILY ACTIVITY CERT BRAG
    body       TEXT        NOT NULL,
    like_count INT         NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(10) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_feed_post_author FOREIGN KEY (author_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE feed_like (
    post_id    BIGINT      NOT NULL,
    member_id  BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (post_id, member_id),
    CONSTRAINT fk_feed_like_post FOREIGN KEY (post_id) REFERENCES feed_post (id),
    CONSTRAINT fk_feed_like_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE feed_comment (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    post_id    BIGINT       NOT NULL,
    parent_id  BIGINT       NULL,             -- NULL이면 댓글, 있으면 답글
    depth      TINYINT      NOT NULL DEFAULT 0, -- 0~1
    author_id  BIGINT       NOT NULL,
    body       VARCHAR(300) NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6)  NULL,
    deleted_by VARCHAR(10)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_feed_comment_post FOREIGN KEY (post_id) REFERENCES feed_post (id),
    CONSTRAINT fk_feed_comment_parent FOREIGN KEY (parent_id) REFERENCES feed_comment (id),
    CONSTRAINT fk_feed_comment_author FOREIGN KEY (author_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE attachment (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    owner_type VARCHAR(10)  NOT NULL,         -- SHARE FEED CERT
    owner_id   BIGINT       NOT NULL,
    object_key VARCHAR(300) NOT NULL,         -- R2 경로. 공개 주소는 저장하지 않는다
    sort_order TINYINT      NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_attachment_owner (owner_type, owner_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE notification (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    member_id       BIGINT       NOT NULL,
    type            VARCHAR(40)  NOT NULL,
    title           VARCHAR(100) NOT NULL,
    body            VARCHAR(300) NULL,
    target_type     VARCHAR(30)  NOT NULL,
    target_id       BIGINT       NOT NULL,
    sub_target_type VARCHAR(30)  NULL,
    sub_target_id   BIGINT       NULL,
    read_at         DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =========================================================
-- 5 · 어드민 로그 (다른 테이블과 연결 없음)
-- =========================================================

CREATE TABLE error_log (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    fingerprint CHAR(64)      NOT NULL,       -- 같은 에러 묶기 (해시)
    message     VARCHAR(1000) NULL,
    stack       MEDIUMTEXT    NULL,
    path        VARCHAR(500)  NULL,
    count       INT           NOT NULL DEFAULT 1,
    first_at    DATETIME(6)   NOT NULL,
    last_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_error_log_fingerprint (fingerprint)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE batch_log (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    job_name   VARCHAR(50)   NOT NULL,
    state      VARCHAR(10)   NOT NULL,        -- SUCCESS FAILED
    message    VARCHAR(1000) NULL,
    started_at DATETIME(6)   NOT NULL,
    ended_at   DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_batch_log_job_started (job_name, started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_log (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    kind       VARCHAR(20) NOT NULL,          -- EMBED_QUESTION EMBED_MEMBER FIND_SENIOR
    target_id  BIGINT      NULL,
    model      VARCHAR(50) NULL,
    latency_ms INT         NULL,
    state      VARCHAR(10) NOT NULL,          -- SUCCESS FAILED FALLBACK
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ai_log_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =========================================================
-- 서로 참조하는 FK
-- =========================================================

ALTER TABLE member
    ADD CONSTRAINT fk_member_invite_code FOREIGN KEY (invite_code_id) REFERENCES invite_code (id);

ALTER TABLE question
    ADD CONSTRAINT fk_question_adopted_answer FOREIGN KEY (adopted_answer_id) REFERENCES answer (id);

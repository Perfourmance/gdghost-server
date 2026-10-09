-- ERD 6장 필수 인덱스. FK 인덱스는 MySQL이 자동으로 만든다
-- 더 필요하면 EXPLAIN으로 확인한 뒤 새 마이그레이션으로 추가한다
-- FULLTEXT ngram은 ngram_token_size=2(기본)로 한국어 두 글자 검색

CREATE INDEX idx_question_deleted_created ON question (deleted_at, created_at);
CREATE INDEX idx_question_state_created ON question (state, created_at);
CREATE FULLTEXT INDEX ft_question_title_body ON question (title, body) WITH PARSER ngram;

CREATE INDEX idx_answer_question_created ON answer (question_id, created_at);
CREATE INDEX idx_answer_author_anonymous ON answer (author_id, is_anonymous);

CREATE INDEX idx_routing_member_sent ON routing (member_id, sent_at);
CREATE INDEX idx_routing_state_created ON routing (state, created_at);
-- routing(question_id)는 FK 인덱스로 이미 있다

CREATE INDEX idx_share_category_save ON share (category, save_count, id);
CREATE INDEX idx_share_created ON share (created_at);
CREATE FULLTEXT INDEX ft_share_title_body ON share (title, body) WITH PARSER ngram;

CREATE FULLTEXT INDEX ft_member_nickname_status_note ON member (nickname, status_note) WITH PARSER ngram;
CREATE INDEX idx_member_generation_status ON member (generation, status);

CREATE INDEX idx_feed_post_created ON feed_post (created_at);
CREATE INDEX idx_notice_created ON notice (created_at);

CREATE INDEX idx_schedule_start_end ON schedule (start_date, end_date);

CREATE INDEX idx_recruit_state_meet ON recruit (state, meet_at);

CREATE INDEX idx_email_verification_email_purpose_created ON email_verification (email, purpose, created_at);

CREATE INDEX idx_notification_member_read_created ON notification (member_id, read_at, created_at);

CREATE INDEX idx_ops_audit_log_target_created ON ops_audit_log (target_member_id, created_at);
CREATE INDEX idx_ops_audit_log_action_created ON ops_audit_log (action, created_at);

CREATE INDEX idx_coffee_chat_receiver_state ON coffee_chat (receiver_id, state);
CREATE INDEX idx_coffee_chat_pair_responded ON coffee_chat (requester_id, receiver_id, responded_at);

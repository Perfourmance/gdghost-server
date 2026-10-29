-- 신고 처리는 같은 대상의 신고를 FOR UPDATE로 잠근다. 인덱스가 없으면 훑은 행을 모두 잠근다
CREATE INDEX idx_report_target ON report (target_type, target_id);

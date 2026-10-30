-- cleanup_nightly가 보관 기간 지난 알림을 지울 때 표 전체를 훑어 잠그지 않게
CREATE INDEX idx_notification_created ON notification (created_at);

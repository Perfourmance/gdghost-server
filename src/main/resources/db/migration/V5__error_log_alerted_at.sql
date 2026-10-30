-- 개발자 디스코드에 마지막으로 알린 시각. 같은 에러는 1시간에 한 번만 알린다
ALTER TABLE error_log ADD COLUMN alerted_at DATETIME(6) NULL;

ALTER TABLE schedule ADD COLUMN updated_by BIGINT NULL AFTER updated_at,
    ADD CONSTRAINT fk_schedule_updated_by FOREIGN KEY (updated_by) REFERENCES member (id);

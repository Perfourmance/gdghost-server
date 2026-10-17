package com.gdghost.member;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailChangeLogRepository extends JpaRepository<EmailChangeLog, Long> {
}

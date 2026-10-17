package com.gdghost.member;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailChangeLogRepository extends JpaRepository<EmailChangeLog, Long> {

    Optional<EmailChangeLog> findFirstByMemberIdAndNewEmailAndVerifiedAtIsNullOrderByIdDesc(long memberId,
            String newEmail);

}

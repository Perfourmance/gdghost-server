package com.gdghost.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findFirstByEmailAndPurposeOrderByIdDesc(String email, VerificationPurpose purpose);

    long countByEmailAndPurposeAndCreatedAtGreaterThanEqual(String email, VerificationPurpose purpose,
            Instant from);

}

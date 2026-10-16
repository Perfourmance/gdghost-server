package com.gdghost.auth;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findFirstByEmailAndPurposeOrderByIdDesc(String email, VerificationPurpose purpose);

    Optional<EmailVerification> findFirstByEmailAndPurposeInOrderByIdDesc(String email,
            Collection<VerificationPurpose> purposes);

    long countByEmailAndPurposeInAndCreatedAtGreaterThanEqual(String email, Collection<VerificationPurpose> purposes,
            Instant from);

}

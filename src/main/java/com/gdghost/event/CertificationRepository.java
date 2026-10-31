package com.gdghost.event;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface CertificationRepository extends JpaRepository<Certification, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Certification c WHERE c.id = :id")
    Optional<Certification> findForUpdate(long id);

}

package com.gdghost.share;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface ShareRepository extends JpaRepository<Share, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Share s WHERE s.id = :id")
    Optional<Share> findForUpdate(long id);

}

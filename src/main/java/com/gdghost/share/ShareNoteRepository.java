package com.gdghost.share;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface ShareNoteRepository extends JpaRepository<ShareNote, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM ShareNote n WHERE n.id = :id")
    Optional<ShareNote> findForUpdate(long id);

}

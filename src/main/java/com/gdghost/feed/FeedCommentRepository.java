package com.gdghost.feed;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface FeedCommentRepository extends JpaRepository<FeedComment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM FeedComment c WHERE c.id = :id")
    Optional<FeedComment> findForUpdate(long id);

}

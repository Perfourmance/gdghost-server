package com.gdghost.feed;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface FeedPostRepository extends JpaRepository<FeedPost, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM FeedPost p WHERE p.id = :id")
    Optional<FeedPost> findForUpdate(long id);

}

package com.gdghost.notice;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** @return 처음이면 1, 이미 보냈으면 0 */
    @Modifying
    @Query("UPDATE Notice n SET n.remindedAt = :now WHERE n.id = :id AND n.remindedAt IS NULL")
    int markReminded(@Param("id") long id, @Param("now") Instant now);

}

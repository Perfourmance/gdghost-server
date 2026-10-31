package com.gdghost.recruit;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface RecruitRepository extends JpaRepository<Recruit, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Recruit r WHERE r.id = :id")
    Optional<Recruit> findForUpdate(long id);

}

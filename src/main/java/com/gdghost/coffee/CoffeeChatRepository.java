package com.gdghost.coffee;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface CoffeeChatRepository extends JpaRepository<CoffeeChat, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CoffeeChat c WHERE c.id = :id")
    Optional<CoffeeChat> findByIdForUpdate(long id);

}

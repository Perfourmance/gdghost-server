package com.gdghost.coffee;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gdghost.common.batch.BatchRunner;

@Component
public class CoffeeExpireJob {

    static final String NAME = "coffee_expire_nightly";

    private final BatchRunner batch;
    private final CoffeeChatWriter writer;

    public CoffeeExpireJob(BatchRunner batch, CoffeeChatWriter writer) {
        this.batch = batch;
        this.writer = writer;
    }

    @Scheduled(cron = "0 10 3 * * *", zone = "Asia/Seoul")
    public void run() {
        batch.run(NAME, () -> "expired=" + writer.expireOverdue() + " memoCleared=" + writer.clearClosedMemos());
    }

}

package com.gdghost.recruit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gdghost.common.batch.BatchRunner;

/** 만나는 시각이 지난 모집을 닫는다. 그 사이에도 API는 시간으로 마감을 판단한다 */
@Component
public class RecruitCloseJob {

    public static final String NAME = "recruit_close_10min";

    private final BatchRunner batch;
    private final RecruitWriter writer;

    public RecruitCloseJob(BatchRunner batch, RecruitWriter writer) {
        this.batch = batch;
        this.writer = writer;
    }

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void run() {
        batch.run(NAME, () -> "closed=" + writer.closePassed());
    }

}

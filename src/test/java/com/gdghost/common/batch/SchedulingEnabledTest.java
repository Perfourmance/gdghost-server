package com.gdghost.common.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.TestPropertySource;

import com.gdghost.support.IntegrationTest;

/** 운영처럼 스케줄을 켰을 때 — 스케줄러도 TaskExecutor라 @Async 실행기가 바뀌지 않는지 함께 본다 */
@IntegrationTest
@TestPropertySource(properties = "batch.scheduling-enabled=true")
@Import(SchedulingEnabledTest.AsyncProbeConfig.class)
class SchedulingEnabledTest {

    @Autowired
    ScheduledTaskHolder tasks;

    @Autowired
    AsyncProbe probe;

    @Test
    void 정리_배치가_매일_03시_KST로_등록된다() {
        assertThat(tasks.getScheduledTasks())
                .map(scheduled -> scheduled.getTask())
                .filteredOn(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .filteredOn(task -> task.toString().contains("batch.CleanupJob.run"))
                .singleElement()
                .satisfies(task -> assertThat(task.getExpression()).isEqualTo("0 0 3 * * *"));
    }

    @Test
    void 사진_정리_배치가_매일_0330_KST로_등록된다() {
        assertThat(tasks.getScheduledTasks())
                .map(scheduled -> scheduled.getTask())
                .filteredOn(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .filteredOn(task -> task.toString().contains("UploadCleanupJob.run"))
                .singleElement()
                .satisfies(task -> assertThat(task.getExpression()).isEqualTo("0 30 3 * * *"));
    }

    @Test
    void 마감_임박_배치가_매일_09시_KST로_등록된다() {
        assertThat(tasks.getScheduledTasks())
                .map(scheduled -> scheduled.getTask())
                .filteredOn(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .filteredOn(task -> task.toString().contains("DeadlineJob.run"))
                .singleElement()
                .satisfies(task -> assertThat(task.getExpression()).isEqualTo("0 0 9 * * *"));
    }

    @Test
    void 모집_닫기_배치가_10분마다_등록된다() {
        assertThat(tasks.getScheduledTasks())
                .map(scheduled -> scheduled.getTask())
                .filteredOn(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .filteredOn(task -> task.toString().contains("RecruitCloseJob.run"))
                .singleElement()
                .satisfies(task -> assertThat(task.getExpression()).isEqualTo("0 */10 * * * *"));
    }

    @Test
    void 재알림_푸시가_매일_08시_KST로_등록된다() {
        assertThat(tasks.getScheduledTasks())
                .map(scheduled -> scheduled.getTask())
                .filteredOn(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .filteredOn(task -> task.toString().contains("NoticeReminderPushJob.run"))
                .singleElement()
                .satisfies(task -> assertThat(task.getExpression()).isEqualTo("0 0 8 * * *"));
    }

    @Test
    void 비동기_리스너는_계속_async_스레드에서_돈다() {
        assertThat(probe.threadName().join()).startsWith("async-");
    }

    @TestConfiguration
    static class AsyncProbeConfig {

        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }

    }

    static class AsyncProbe {

        @Async
        public CompletableFuture<String> threadName() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }

    }

}

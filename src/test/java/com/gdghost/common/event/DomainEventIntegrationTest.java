package com.gdghost.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.support.IntegrationTest;

/**
 * 실제 이벤트가 아직 없어 시험용 이벤트 · 리스너를 붙인다.
 */
@IntegrationTest
@Import({DomainEventIntegrationTest.ProbeService.class, DomainEventIntegrationTest.ProbeListener.class})
class DomainEventIntegrationTest {

    @Autowired
    ProbeService service;

    @Autowired
    ProbeListener listener;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 커밋된_뒤_다른_스레드에서_받는다() throws Exception {
        service.publish(new ProbeEvent(1));

        Received received = listener.received().poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.id()).isEqualTo(1);
        assertThat(received.thread()).startsWith("async-");
    }

    @Test
    void 롤백되면_받지_않는다() throws Exception {
        assertThatThrownBy(() -> service.publishAndFail(new ProbeEvent(2)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(listener.received().poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void 리스너가_실패해도_요청은_성공하고_error_log에_남는다() throws Exception {
        service.publish(new FailingEvent(3));

        assertThat(waitForErrorLog()).isEqualTo("async ProbeListener.onFailing");
    }

    private String waitForErrorLog() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            var paths = jdbc.queryForList("SELECT path FROM error_log", String.class);
            if (!paths.isEmpty()) {
                return paths.getFirst();
            }
            Thread.sleep(50);
        }
        return null;
    }

    record ProbeEvent(long id) {
    }

    record FailingEvent(long id) {
    }

    record Received(long id, String thread) {
    }

    static class ProbeService {

        private final ApplicationEventPublisher events;

        ProbeService(ApplicationEventPublisher events) {
            this.events = events;
        }

        @Transactional
        public void publish(Object event) {
            events.publishEvent(event);
        }

        @Transactional
        public void publishAndFail(Object event) {
            events.publishEvent(event);
            throw new IllegalStateException("롤백");
        }

    }

    static class ProbeListener {

        // @Async 프록시라 필드가 아니라 메서드로 꺼낸다
        private final BlockingQueue<Received> received = new LinkedBlockingQueue<>();

        public BlockingQueue<Received> received() {
            return received;
        }

        @DomainEventListener
        public void on(ProbeEvent event) {
            received.add(new Received(event.id(), Thread.currentThread().getName()));
        }

        @DomainEventListener
        public void onFailing(FailingEvent event) {
            throw new IllegalStateException("리스너 실패");
        }

    }

}

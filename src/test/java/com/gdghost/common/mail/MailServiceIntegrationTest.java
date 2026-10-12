package com.gdghost.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorLogRecorder;
import com.gdghost.support.FakeMailGateway;
import com.gdghost.support.IntegrationTest;
import com.gdghost.support.MutableClock;

@IntegrationTest
class MailServiceIntegrationTest {

    private static final Mail MAIL = new Mail("hong@gmail.com", "[GDGoC] 인증번호", "123456");

    @Autowired
    MailService mailService;

    @Autowired
    FakeMailGateway gateway;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void clear() {
        gateway.clear();
    }

    @Test
    void 트랜잭션_밖에서는_바로_보낸다() throws Exception {
        mailService.send(MAIL);

        assertThat(await(() -> !gateway.sent().isEmpty())).isTrue();
        assertThat(gateway.sent()).containsExactly(MAIL);
    }

    @Test
    void 트랜잭션_안에서는_커밋된_뒤에_보낸다() throws Exception {
        tx.executeWithoutResult(status -> {
            mailService.send(MAIL);
            sleep(300);
            assertThat(gateway.sent()).isEmpty();
        });

        assertThat(await(() -> !gateway.sent().isEmpty())).isTrue();
    }

    @Test
    void 롤백되면_보내지_않는다() throws Exception {
        tx.executeWithoutResult(status -> {
            mailService.send(MAIL);
            status.setRollbackOnly();
        });

        Thread.sleep(500);
        assertThat(gateway.sent()).isEmpty();
    }

    @Test
    void 발송_실패는_error_log에_남는다() throws Exception {
        gateway.failWith(new IllegalStateException("SMTP 응답 없음"));

        mailService.send(MAIL);

        assertThat(await(() -> !errorLogPaths().isEmpty())).isTrue();
        assertThat(errorLogPaths()).containsExactly("mail [GDGoC] 인증번호");
    }

    @Test
    void 한도를_넘으면_보내지_않고_503() {
        MailService exhausted = new MailService(gateway,
                new MailQuota(new MutableClock(Instant.parse("2026-10-11T03:00:00Z")), 0),
                Runnable::run, mock(ErrorLogRecorder.class));

        assertThatThrownBy(() -> exhausted.send(MAIL)).isInstanceOf(BusinessException.class);
        assertThat(gateway.sent()).isEmpty();
    }

    @Test
    void 헬스_체크는_SMTP에_접속하지_않는다() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private List<String> errorLogPaths() {
        return jdbc.queryForList("SELECT path FROM error_log", String.class);
    }

    private static boolean await(Supplier<Boolean> condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (condition.get()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}

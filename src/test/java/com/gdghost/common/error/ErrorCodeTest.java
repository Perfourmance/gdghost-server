package com.gdghost.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void 에러_코드_DB와_개수가_같다() {
        assertThat(ErrorCode.values()).hasSize(54);
    }

    @Test
    void type은_코드를_케밥_케이스로_쓴_주소다() {
        assertThat(ErrorCode.IDEMPOTENCY_KEY_REUSED.type())
                .hasToString("https://gdghost.dev/errors/idempotency-key-reused");
    }

}

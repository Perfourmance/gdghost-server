package com.gdghost.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmbeddingConfigTest {

    private final EmbeddingConfig config = new EmbeddingConfig();

    @Test
    void 운영에서_값이_비면_뜨지_않는다() {
        assertThatThrownBy(() -> config.embeddingClient("", "token", true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.embeddingClient("account", "", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 로컬은_비어_있으면_호출하지_않는다() {
        assertThat(config.embeddingClient("", "", false).enabled()).isFalse();
    }

}

package com.gdghost.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DiscordConfigTest {

    private final DiscordConfig config = new DiscordConfig();

    @Test
    void 운영에서_주소가_비었거나_https가_아니면_뜨지_않는다() {
        assertThatThrownBy(() -> config.discordGateway("https://a", "", "https://c", true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("STAFF");
        assertThatThrownBy(() -> config.discordGateway("https://a", "https://b", "http://c", true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DEV");
    }

    @Test
    void 로컬은_비어_있어도_뜬다() {
        assertThatCode(() -> config.discordGateway("", "", "", false)).doesNotThrowAnyException();
    }

}

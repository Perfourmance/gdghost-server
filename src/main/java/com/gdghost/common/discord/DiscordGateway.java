package com.gdghost.common.discord;

/**
 * 디스코드로 실제로 내보내는 곳. 서비스 · 리스너는 이걸 직접 쓰지 않고 DiscordNotifier를 쓴다(비동기 · 실패 기록).
 */
public interface DiscordGateway {

    void send(DiscordChannel channel, DiscordMessage message);

}

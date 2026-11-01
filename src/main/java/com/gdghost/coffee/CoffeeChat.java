package com.gdghost.coffee;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoffeeChat {

    static final Duration RESPONSE_WINDOW = Duration.ofDays(7);
    static final Duration MEMO_WINDOW = Duration.ofDays(14);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long requesterId;
    private long receiverId;
    private String purpose;

    @Enumerated(EnumType.STRING)
    private CoffeeChatState state;

    private Instant respondedAt;
    private String acceptMemo;
    private Instant createdAt;

    static CoffeeChat request(long requesterId, long receiverId, String purpose, Instant now) {
        CoffeeChat chat = new CoffeeChat();
        chat.requesterId = requesterId;
        chat.receiverId = receiverId;
        chat.purpose = purpose;
        chat.state = CoffeeChatState.REQUESTED;
        chat.createdAt = now;
        return chat;
    }

    /** 배치가 돌기 전이어도 7일이 지났으면 응답할 수 없다 */
    boolean isRespondable(Instant now) {
        return state == CoffeeChatState.REQUESTED && now.isBefore(createdAt.plus(RESPONSE_WINDOW));
    }

    void accept(String memo, Instant now) {
        this.state = CoffeeChatState.ACCEPTED;
        this.acceptMemo = memo;
        this.respondedAt = now;
    }

    void decline(Instant now) {
        this.state = CoffeeChatState.DECLINED;
        this.respondedAt = now;
    }

}

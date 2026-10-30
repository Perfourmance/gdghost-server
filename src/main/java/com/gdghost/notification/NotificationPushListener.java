package com.gdghost.notification;

import org.springframework.stereotype.Component;

import com.gdghost.common.event.DomainEventListener;

/** 커밋 후 비동기로 보낸다. 트랜잭션이 롤백되면 나가지 않는다 */
@Component
public class NotificationPushListener {

    private final NotificationPusher pusher;

    public NotificationPushListener(NotificationPusher pusher) {
        this.pusher = pusher;
    }

    @DomainEventListener
    public void on(NotificationPushEvent event) {
        pusher.push(event);
    }

}

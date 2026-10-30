package com.gdghost.notification;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.gdghost.common.error.ErrorLogRecorder;
import com.gdghost.common.event.DomainEventListener;
import com.gdghost.common.push.PushGateway;
import com.gdghost.common.push.PushResult;
import com.gdghost.common.push.PushTarget;

import tools.jackson.databind.ObjectMapper;

/** 보내고 끝 — 앱 내 알림은 이미 저장돼 있다. 한 기기가 실패해도 나머지는 보낸다 */
@Component
public class NotificationPushListener {

    private final PushSubscriptionRepository subscriptions;
    private final PushGateway gateway;
    private final ErrorLogRecorder errorLog;
    private final ObjectMapper json;

    public NotificationPushListener(PushSubscriptionRepository subscriptions, PushGateway gateway,
            ErrorLogRecorder errorLog, ObjectMapper json) {
        this.subscriptions = subscriptions;
        this.gateway = gateway;
        this.errorLog = errorLog;
        this.json = json;
    }

    @DomainEventListener
    public void on(NotificationPushEvent event) {
        String payload = json.writeValueAsString(payload(event));
        for (PushTarget target : subscriptions.targets(event.memberId(), event.excludedId())) {
            try {
                if (gateway.send(target, payload) == PushResult.GONE) {
                    subscriptions.deleteGone(target.endpoint());
                }
            } catch (RuntimeException e) {
                errorLog.record(e, "push " + event.type());
            }
        }
    }

    private static Map<String, Object> payload(NotificationPushEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", event.type().name());
        payload.put("title", event.title());
        payload.put("body", event.body());
        payload.put("target", event.target());
        return payload;
    }

}

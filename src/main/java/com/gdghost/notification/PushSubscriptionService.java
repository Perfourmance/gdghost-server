package com.gdghost.notification;

import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.push.PushEndpoints;
import com.gdghost.common.push.PushTarget;

@Service
public class PushSubscriptionService {

    private static final int P256DH_BYTES = 65;
    private static final int AUTH_BYTES = 16;

    private final PushSubscriptionRepository subscriptions;

    public PushSubscriptionService(PushSubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    public record Saved(long id, boolean created) {
    }

    @Transactional
    public Saved subscribe(long memberId, PushSubscriptionRequest request) {
        if (!PushEndpoints.isAllowed(request.endpoint())) {
            throw invalid("endpoint", "브라우저 푸시 서비스 주소가 아닙니다.");
        }
        checkKey("keys.p256dh", request.keys().p256dh(), P256DH_BYTES);
        checkKey("keys.auth", request.keys().auth(), AUTH_BYTES);
        boolean existed = subscriptions.exists(request.endpoint());
        long id = subscriptions.save(memberId,
                new PushTarget(request.endpoint(), request.keys().p256dh(), request.keys().auth()));
        return new Saved(id, !existed);
    }

    /** 내 구독만 지운다. 없는 주소도 성공 */
    @Transactional
    public void unsubscribe(long memberId, String endpoint) {
        subscriptions.delete(memberId, endpoint);
    }

    private static void checkKey(String field, String value, int bytes) {
        try {
            if (Base64.getUrlDecoder().decode(value).length == bytes) {
                return;
            }
        } catch (IllegalArgumentException e) {
            // 아래에서 같은 에러로
        }
        throw invalid(field, "브라우저가 만든 구독 값이 아닙니다.");
    }

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, null, List.of(new ErrorField(field, message)));
    }

}

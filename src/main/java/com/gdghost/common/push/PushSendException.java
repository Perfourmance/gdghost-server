package com.gdghost.common.push;

/** 메시지에 구독 주소를 넣지 않는다 — 주소를 알면 그 기기로 보낼 수 있는 값이라 */
public class PushSendException extends RuntimeException {

    public PushSendException(String message) {
        super(message);
    }

    public PushSendException(String message, Throwable cause) {
        super(message, cause);
    }

}

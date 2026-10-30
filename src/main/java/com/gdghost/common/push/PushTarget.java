package com.gdghost.common.push;

/** 브라우저가 만든 구독 정보. p256dh · auth는 base64url */
public record PushTarget(String endpoint, String p256dh, String auth) {
}

package com.gdghost.common.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PushEndpointsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fcm.googleapis.com/fcm/send/abc",
            "https://updates.push.services.mozilla.com/wpush/v2/abc",
            "https://wns2-par02p.notify.windows.com/w/?token=abc",
            "https://web.push.apple.com/abc"})
    void 브라우저_푸시_서비스는_받는다(String endpoint) {
        assertThat(PushEndpoints.isAllowed(endpoint)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/abc",
            "https://169.254.169.254/latest/meta-data",
            "https://localhost/abc",
            "https://evil.com/fcm.googleapis.com",
            "https://fcm.googleapis.com.evil.com/abc",
            "https://notpush.apple.com/abc",
            "https://fcm.googleapis.com@evil.com/abc",
            "https://user@fcm.googleapis.com/abc",
            "https://fcm.googleapis.com:8443/abc",
            "not a url"})
    void 그_밖의_주소는_막는다(String endpoint) {
        assertThat(PushEndpoints.isAllowed(endpoint)).isFalse();
    }

}

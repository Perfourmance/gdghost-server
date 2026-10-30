package com.gdghost.common.push;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * 구독 주소는 서버가 그대로 POST하는 값이다. 내부망으로 요청을 보내게 만들 수 없도록
 * 브라우저 푸시 서비스 주소만 받는다.
 */
public final class PushEndpoints {

    private static final List<String> EXACT = List.of("fcm.googleapis.com");
    private static final List<String> SUFFIXES = List.of(".push.services.mozilla.com", ".notify.windows.com",
            ".push.apple.com");

    private PushEndpoints() {
    }

    public static boolean isAllowed(String endpoint) {
        try {
            URI uri = new URI(endpoint);
            if (!"https".equals(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getHost() == null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return EXACT.contains(host) || SUFFIXES.stream().anyMatch(host::endsWith);
        } catch (URISyntaxException e) {
            return false;
        }
    }

}

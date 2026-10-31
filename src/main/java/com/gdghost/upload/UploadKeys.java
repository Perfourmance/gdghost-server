package com.gdghost.upload;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** objectKey 모양: feed/2026/10/51/7f3a…c.webp (용도/연/월/부원 id/무작위). 부원 id로 남의 키를 가려낸다 */
final class UploadKeys {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern PATTERN = Pattern.compile("(share|feed|cert)/\\d{4}/\\d{2}/(\\d+)/[0-9a-f]{32}\\.(jpg|png|webp)");

    private UploadKeys() {
    }

    static String generate(UploadPurpose purpose, long memberId, String extension, Instant now) {
        ZonedDateTime date = now.atZone(KST);
        return "%s/%d/%02d/%d/%s.%s".formatted(purpose.name().toLowerCase(Locale.ROOT), date.getYear(),
                date.getMonthValue(), memberId, UUID.randomUUID().toString().replace("-", ""), extension);
    }

    /** 이 서비스가 만든 모양인지. 정리 배치가 다른 파일을 지우지 않게 한다 */
    static boolean isOurs(String objectKey) {
        return PATTERN.matcher(objectKey).matches();
    }

    static boolean belongsTo(String objectKey, UploadPurpose purpose, long memberId) {
        var matcher = PATTERN.matcher(objectKey);
        return matcher.matches()
                && objectKey.startsWith(purpose.name().toLowerCase(Locale.ROOT) + "/")
                && matcher.group(2).equals(Long.toString(memberId));
    }

}

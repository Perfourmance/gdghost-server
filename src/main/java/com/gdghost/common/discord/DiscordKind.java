package com.gdghost.common.discord;

/**
 * 알림 종류별 모양. 썸네일은 프론트 public/discord/의 파일.
 * headline이면 description(사용자 글 제목)을 큰 링크 제목으로, title(분류)을 윗줄로 올린다.
 */
public enum DiscordKind {

    QUESTION("💬", 0x2466DD, "discord-question.png", true, "답해 줄 선배를 기다리고 있어요"),
    NOTICE("📢", 0x1769F4, "discord-notice.png", true, "새 공지가 올라왔어요"),
    NOTICE_URGENT("🚨", 0xE5484D, "discord-notice-urgent.png", true, "필독 공지예요. 꼭 확인해 주세요"),
    DEADLINE("⏰", 0xF59E0B, "discord-deadline.png", false, null),
    UNANSWERED("⏳", 0xA9C7FF, "discord-staff.png", true, "1시간째 답이 없어요. 먼저 답해 주세요"),
    REPORT("🚩", 0xE5484D, "discord-staff.png", false, null),
    STAFF("🛡️", 0xA9C7FF, "discord-staff.png", false, null),
    DEV("🛠️", 0x53647A, "discord-dev.png", false, null),
    DEV_ALERT("⚠️", 0xE5484D, "discord-dev.png", false, null);

    private final String emoji;
    private final int color;
    private final String image;
    private final boolean headline;
    private final String guide;

    DiscordKind(String emoji, int color, String image, boolean headline, String guide) {
        this.emoji = emoji;
        this.color = color;
        this.image = image;
        this.headline = headline;
        this.guide = guide;
    }

    public String emoji() {
        return emoji;
    }

    public int color() {
        return color;
    }

    public String image() {
        return image;
    }

    public boolean headline() {
        return headline;
    }

    public String guide() {
        return guide;
    }

}

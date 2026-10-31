package com.gdghost.event;

/** 비워 두면 고정 O · 필독 X */
public record EventNoticeRequest(Boolean isPinned, Boolean isMustRead) {
}

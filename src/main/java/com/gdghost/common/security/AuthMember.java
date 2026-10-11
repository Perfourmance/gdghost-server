package com.gdghost.common.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 로그인한 부원. 컨트롤러에서 {@code @AuthenticationPrincipal AuthMember me}로 받는다.
 * role은 요청마다 DB에서 다시 읽은 값.
 */
public record AuthMember(long id, Role role) {

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

}

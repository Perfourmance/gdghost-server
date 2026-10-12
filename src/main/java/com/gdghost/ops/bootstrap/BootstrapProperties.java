package com.gdghost.ops.bootstrap;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서비스 첫 시작용 환경변수 (application.yaml의 bootstrap.*).
 *
 * @param inviteCode   invite_code가 0건일 때 만들 준비용 코드
 * @param generation   준비용 코드의 기수
 * @param devLoginIds  DEV로 만들 아이디
 */
@ConfigurationProperties("bootstrap")
public record BootstrapProperties(String inviteCode, Integer generation, List<String> devLoginIds) {

    public BootstrapProperties {
        inviteCode = inviteCode == null || inviteCode.isBlank() ? null : inviteCode.strip();
        devLoginIds = devLoginIds == null ? List.of()
                : devLoginIds.stream().map(String::strip).filter(id -> !id.isEmpty()).toList();
    }

}

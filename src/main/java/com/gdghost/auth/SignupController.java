package com.gdghost.auth;

import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.ratelimit.RateLimit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Tag(name = "가입")
@RestController
@RequestMapping("/api/v1/auth/signup")
public class SignupController {

    private final SignupService signupService;

    public SignupController(SignupService signupService) {
        this.signupService = signupService;
    }

    @Operation(summary = "초대 코드 확인",
            description = "유효하면 signupToken(30분)을 준다. 10분에 같은 기기 5회 · 같은 IP 50회 틀리면 잠긴다. "
                    + "성공 · 실패 합쳐 IP당 분당 20회.")
    // 유출된 코드로 세션을 찍어내 정상 가입자의 세션이 밀려나지 않게
    @RateLimit(limit = 20, per = RateLimit.Per.IP)
    @PostMapping("/invite-code")
    public InviteCodeCheckResponse checkInviteCode(@RequestBody @Valid InviteCodeCheckRequest request,
            @CookieValue(name = SignupDeviceCookie.NAME, required = false) String deviceId,
            HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        // 에러 응답에도 실려야 다음 실패가 같은 기기로 쌓이므로 먼저 붙인다
        if (!SignupDeviceCookie.isValid(deviceId)) {
            deviceId = SignupDeviceCookie.newId();
            servletResponse.addHeader(HttpHeaders.SET_COOKIE, SignupDeviceCookie.of(deviceId).toString());
        }
        return signupService.checkInviteCode(request.code(), deviceId, servletRequest.getRemoteAddr());
    }

    @Operation(summary = "가입 인증번호 발송",
            description = "6자리 · 10분. 60초 안 재발송 불가, 같은 메일 하루 5회, signupToken당 5통, IP당 시간당 20회.")
    // 메일 주소 · 토큰을 바꿔 가며 서버 하루 메일 한도를 소진하지 못하게
    @RateLimit(limit = 20, unit = ChronoUnit.HOURS, per = RateLimit.Per.IP)
    @PostMapping("/email-verifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VerificationSentResponse sendEmailVerification(@RequestBody @Valid SignupEmailRequest request) {
        return signupService.sendEmailVerification(request);
    }

    @Operation(summary = "가입 인증번호 확인", description = "5회 틀리면 번호가 무효가 된다.")
    @PostMapping("/email-verifications/confirm")
    public EmailVerifiedResponse confirmEmailVerification(@RequestBody @Valid SignupEmailConfirmRequest request) {
        return signupService.confirmEmailVerification(request);
    }

}

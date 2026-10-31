package com.gdghost.common.ratelimit;

import java.time.Duration;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.gdghost.common.security.AuthMember;

@Aspect
@Component
@Order(2)
public class RateLimitAspect {

    private final RateLimiter limiter;

    public RateLimitAspect(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Before("@annotation(rateLimit)")
    void check(JoinPoint joinPoint, RateLimit rateLimit) {
        Duration window = Duration.of(rateLimit.window(), rateLimit.unit());
        String key = joinPoint.getSignature().toLongString() + "|" + window + "|" + subject(rateLimit.per());
        long retryAfter = limiter.tryAcquire(key, rateLimit.limit(), window);
        if (retryAfter > 0) {
            throw new RateLimitedException(retryAfter);
        }
    }

    /** 같은 메서드에 @RateLimit이 여러 개면 컨테이너로 묶여 들어온다 */
    @Before("@annotation(limits)")
    void checkAll(JoinPoint joinPoint, RateLimit.List limits) {
        for (RateLimit rateLimit : limits.value()) {
            check(joinPoint, rateLimit);
        }
    }

    // 운영 서버가 프록시 뒤에 있으면 server.forward-headers-strategy 설정이 있어야 실제 IP가 잡힌다
    private static String subject(RateLimit.Per per) {
        if (per == RateLimit.Per.MEMBER
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof AuthMember member) {
            return "member:" + member.id();
        }
        return "ip:" + ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest().getRemoteAddr();
    }

}

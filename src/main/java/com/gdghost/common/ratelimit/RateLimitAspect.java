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
        String key = joinPoint.getSignature().toLongString() + "|" + subject(rateLimit.per());
        long retryAfter = limiter.tryAcquire(key, rateLimit.limit(), Duration.of(rateLimit.window(), rateLimit.unit()));
        if (retryAfter > 0) {
            throw new RateLimitedException(retryAfter);
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

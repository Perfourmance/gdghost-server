package com.gdghost.common.idempotency;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.security.AuthMember;
import com.gdghost.common.security.TokenHasher;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 1대 전제라 메모리(Caffeine)에 기억한다 — 서버가 늘면 DB · Redis로 옮겨야 함.
 * 성공한 응답만 기억하고, 실패하면 키를 풀어 같은 키로 다시 보낼 수 있게 한다.
 * 횟수 제한보다 먼저 돌아 재생 응답은 횟수에 세지 않는다.
 */
@Aspect
@Component
@Order(1)
public class IdempotencyAspect {

    public static final String HEADER = "Idempotency-Key";
    static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_KEY_LENGTH = 100;

    private final Cache<String, Entry> entries = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(10_000)
            .build();
    private final JsonMapper json;

    public IdempotencyAspect(JsonMapper json) {
        this.json = json;
    }

    private sealed interface Entry {
        String hash();
    }

    private record InProgress(String hash) implements Entry {
    }

    private record Done(String hash, Object response) implements Entry {
    }

    @Around("@annotation(com.gdghost.common.idempotency.Idempotent)")
    Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key 헤더가 필요합니다.",
                    List.of(new ErrorField(HEADER, "작성 화면을 열 때 만든 UUID를 보내 주세요.")));
        }

        String entryKey = currentMember().id() + ":" + key;
        String hash = hash(request, joinPoint);
        Entry existing = entries.asMap().putIfAbsent(entryKey, new InProgress(hash));
        if (existing != null) {
            if (!existing.hash().equals(hash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            if (existing instanceof Done done) {
                return done.response();
            }
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "같은 요청을 처리하고 있습니다.");
        }

        try {
            Object response = joinPoint.proceed();
            entries.put(entryKey, new Done(hash, response));
            return response;
        } catch (Throwable e) {
            entries.invalidate(entryKey);
            throw e;
        }
    }

    // 같은 키로 다른 API를 부르거나 본문이 다르면 '다른 내용'
    private String hash(HttpServletRequest request, ProceedingJoinPoint joinPoint) {
        return TokenHasher.sha256(request.getMethod() + " " + request.getRequestURI() + "?" + request.getQueryString()
                + "\n" + json.writeValueAsString(requestBodies(joinPoint)));
    }

    private static List<Object> requestBodies(ProceedingJoinPoint joinPoint) {
        Annotation[][] annotations = ((MethodSignature) joinPoint.getSignature()).getMethod().getParameterAnnotations();
        Object[] args = joinPoint.getArgs();
        List<Object> bodies = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            for (Annotation annotation : annotations[i]) {
                if (annotation instanceof RequestBody) {
                    bodies.add(args[i]);
                }
            }
        }
        return bodies;
    }

    private static AuthMember currentMember() {
        if (SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof AuthMember member) {
            return member;
        }
        throw new IllegalStateException("@Idempotent는 로그인이 필요한 API에만 붙인다");
    }

}

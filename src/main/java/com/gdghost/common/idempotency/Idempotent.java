package com.gdghost.common.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 만드는 POST에 붙인다. Idempotency-Key 헤더 필수, 같은 (부원, 키)가 10분 안에 다시 오면 처음 응답을 그대로 돌려준다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}

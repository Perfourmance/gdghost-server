package com.gdghost.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import com.gdghost.TestcontainersConfiguration;

/**
 * 통합 테스트 공통 설정 — 실제 MySQL(Testcontainers) + MockMvc + Fixtures.
 * 각 테스트 전에 DB를 비운다(태그 시드는 남김).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, Fixtures.class})
@ExtendWith(DatabaseCleaner.class)
public @interface IntegrationTest {
}

package com.gdghost;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트 전체가 MySQL 컨테이너 하나를 같이 쓴다.
 * 컨테이너를 빈으로 두면 Spring 컨텍스트(프로필 · 설정이 다른 테스트)마다 새로 뜨고,
 * 한 컨텍스트가 닫힐 때 같이 멈춘다. 그래서 static으로 한 번만 띄우고 접속 정보만 넘긴다.
 * 종료는 Testcontainers(Ryuk)가 JVM 끝날 때 처리.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    static {
        MYSQL.start();
    }

    @Bean
    DynamicPropertyRegistrar mysqlProperties() {
        return registry -> {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
        };
    }

}

package com.gdghost.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.gdghost.routing.MatchingConfig;

@Configuration
@EnableConfigurationProperties(MatchingConfig.class)
public class MatchingPropertiesConfig {
}

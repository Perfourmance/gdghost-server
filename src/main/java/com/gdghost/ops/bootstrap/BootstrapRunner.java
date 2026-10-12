package com.gdghost.ops.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(BootstrapProperties.class)
public class BootstrapRunner implements ApplicationRunner {

    private final BootstrapService bootstrap;
    private final BootstrapProperties properties;

    public BootstrapRunner(BootstrapService bootstrap, BootstrapProperties properties) {
        this.bootstrap = bootstrap;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrap.apply(properties);
    }

}

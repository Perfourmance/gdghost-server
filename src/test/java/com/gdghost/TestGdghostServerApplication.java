package com.gdghost;

import org.springframework.boot.SpringApplication;

public class TestGdghostServerApplication {

    public static void main(String[] args) {
        SpringApplication.from(GdghostServerApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}

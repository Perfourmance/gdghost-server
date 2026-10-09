package com.gdghost;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GdghostServerApplication {

    public static void main(String[] args) {
        // 서버 시간은 UTC 고정. KST 변환은 날짜(date) 값에서만 한다
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(GdghostServerApplication.class, args);
    }

}

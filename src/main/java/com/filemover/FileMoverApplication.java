package com.filemover;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class FileMoverApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileMoverApplication.class, args);
    }
}

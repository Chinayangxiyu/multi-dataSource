package com.xiyu;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties
@SpringBootApplication
public class MultiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiApplication.class, args);
        System.out.println("---MultiApplication start----");

    }
}

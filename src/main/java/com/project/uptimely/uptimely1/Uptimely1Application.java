package com.project.uptimely.uptimely1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Uptimely1Application {

    public static void main(String[] args) {
        SpringApplication.run(Uptimely1Application.class, args);
    }

}

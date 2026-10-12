package com.poptsov.discordbotse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DiscordBotSeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscordBotSeApplication.class, args);
    }

}

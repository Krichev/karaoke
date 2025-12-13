package com.karaoke;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class KaraokeRatingApiApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(KaraokeRatingApiApplication.class, args);
    }
}

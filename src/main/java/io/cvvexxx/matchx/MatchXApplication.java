package io.cvvexxx.matchx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MatchXApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchXApplication.class, args);
    }

}

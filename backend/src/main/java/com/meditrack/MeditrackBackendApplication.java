package com.meditrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.meditrack.repository")
@EntityScan(basePackages = "com.meditrack.entity")
@EnableJpaAuditing
@EnableScheduling
public class MeditrackBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeditrackBackendApplication.class, args);
    }
}

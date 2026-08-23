package com.cloudfinsight.collectorservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.cloudfinsight.collectorservice.repository")
@EnableRetry
@EnableScheduling
public class CollectorServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CollectorServiceApplication.class, args);
	}

}

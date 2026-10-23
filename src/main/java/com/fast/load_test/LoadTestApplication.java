package com.fast.load_test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class LoadTestApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoadTestApplication.class, args);
	}

}

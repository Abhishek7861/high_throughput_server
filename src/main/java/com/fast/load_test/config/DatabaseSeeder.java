package com.fast.load_test.config;

import com.fast.load_test.entity.Payload;
import com.fast.load_test.repository.PayloadRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

@Configuration
public class DatabaseSeeder {
    @Bean
    CommandLineRunner seedDatabase(PayloadRepository payloadRepository){
        return args -> {
            payloadRepository.deleteAll();
            System.out.println("Deleted All records");
            long start = 0;
            for (start = 1; start <= 100; start += 1) {
                payloadRepository.save(Payload.builder()
                        .createdAt(Instant.now())
                        .hashCode("df2463d4-b6b5-4fec-8690-f9d9e318c3e7")
                        .build());

            }
            System.out.println("Added Records: "+start);
        };
    }
}

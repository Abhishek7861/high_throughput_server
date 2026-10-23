package com.fast.load_test.service;

import com.fast.load_test.entity.Payload;
import com.fast.load_test.repository.PayloadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PayloadService {
    private final PayloadRepository payloadRepository;

    @Cacheable(value = "payloads", key = "#id")
    public Payload findById(Long id){

        System.out.println("Fetching from PostgreSQL");

        return payloadRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("Payload not found"));
    }
}

package com.fast.load_test.service;

import com.fast.load_test.entity.Payload;
import com.fast.load_test.repository.PayloadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PayloadService {
    private final PayloadRepository payloadRepository;

    @Cacheable(value = "payloads", key = "#id")
    public Payload findById(Long id){

        log.info("Redis Miss, Fetching id {} from PostgreSQL",id);

        return payloadRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("Payload not found"));
    }
}

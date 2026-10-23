package com.fast.load_test.controller;

import com.fast.load_test.entity.PayloadDTO;
import com.fast.load_test.service.PayloadEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/stream/payload")
@RequiredArgsConstructor
public class RedisStreamController {
    private final PayloadEventProducer producer;

    @PostMapping
    public ResponseEntity<?> create( @RequestBody PayloadDTO request) {
        String hash = request.getHash();
        if (hash == null || hash.isBlank() || hash.length() > 255) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "hash must contain 1 to 255 characters and cannot be blank");
        }

        String eventId = producer.publish(hash);

        return ResponseEntity.accepted().body(
                Map.of(
                        "status", "QUEUED",
                        "eventId", eventId
                )
        );
    }
}

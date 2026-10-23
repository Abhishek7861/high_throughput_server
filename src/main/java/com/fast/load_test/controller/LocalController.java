package com.fast.load_test.controller;

import com.fast.load_test.entity.Payload;
import com.fast.load_test.entity.PayloadDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/local/payload")
public class LocalController {

    private final Payload payload = Payload.builder()
            .id(1L)
            .createdAt(Instant.now())
            .hashCode("df2463d4-b6b5-4fec-8690-f9d9e318c3e7")
            .build();
    private static final long MIN_SEEDED_ID = 1;
    private static final long MAX_SEEDED_ID = 100;

    @GetMapping
    public ResponseEntity<Payload> findAll() throws NoSuchAlgorithmException {
        return ResponseEntity.status(HttpStatus.OK).body(payload);
    }

    @PostMapping
    public ResponseEntity<Payload> addPayload(@RequestBody PayloadDTO payloadDTO) throws NoSuchAlgorithmException {
        // ThreadLocalRandom avoids contention on a shared Random under load; upper bound is exclusive
        long id = ThreadLocalRandom.current().nextLong(MIN_SEEDED_ID, MAX_SEEDED_ID + 1);

        Payload payloadTemp = Payload.builder()
                .id(id)
                .createdAt(Instant.now())
                .hashCode(payloadDTO.getHash())
                .build();
        // Not stored: this endpoint is an in-memory baseline, and keeping every payload leaked memory under load
        return ResponseEntity.status(HttpStatus.CREATED).body(payloadTemp);
    }

}

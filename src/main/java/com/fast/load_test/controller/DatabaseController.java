package com.fast.load_test.controller;

import com.fast.load_test.entity.Payload;
import com.fast.load_test.entity.PayloadDTO;
import com.fast.load_test.repository.PayloadRepository;
import com.fast.load_test.service.PayloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/database/payload")
@RequiredArgsConstructor
public class DatabaseController {
    private final PayloadRepository payloadRepository;
    private final PayloadService payloadService;
    // Ids seeded by DatabaseSeeder
    private static final long MIN_SEEDED_ID = 1;
    private static final long MAX_SEEDED_ID = 100;

    @GetMapping
    public ResponseEntity<Payload> findById() {
        // ThreadLocalRandom avoids contention on a shared Random under load; upper bound is exclusive
        long id = ThreadLocalRandom.current().nextLong(MIN_SEEDED_ID, MAX_SEEDED_ID + 1);
        return ResponseEntity.ok(payloadService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Payload> addPayload(@RequestBody PayloadDTO payloadDTO) {
        String hash = payloadDTO.getHash();
        if (hash == null || hash.isBlank() || hash.length() > 255) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "hash must contain 1 to 255 characters and cannot be blank");
        }
        Payload payload = Payload.builder()
                .createdAt(Instant.now())
                .hashCode(hash)
                .build();
        Payload saved = payloadRepository.save(payload);
        return ResponseEntity.created(URI.create("/database/payload/" + saved.getId()))
                .body(saved);
    }
}

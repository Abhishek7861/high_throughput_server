package com.fast.load_test.controller;

import com.fast.load_test.entity.Payload;
import com.fast.load_test.entity.PayloadDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class LocalController {

    private final Payload payload = Payload.builder()
            .id(1L)
            .createdAt(Instant.now())
            .hashCode("df2463d4-b6b5-4fec-8690-f9d9e318c3e7")
            .build();
    private final List<Payload> payloadList = new ArrayList<>();

    @GetMapping
    public ResponseEntity<Payload> findAll() throws NoSuchAlgorithmException {
        return ResponseEntity.status(HttpStatus.OK).body(payload);
    }

    @PostMapping
    public ResponseEntity<Payload> addPayload(@RequestBody PayloadDTO payloadDTO) throws NoSuchAlgorithmException {
        Payload payloadTemp = Payload.builder()
                .id(1L)
                .createdAt(Instant.now())
                .hashCode(payloadDTO.getHash())
                .build();
        payloadList.add(payloadTemp);
        return ResponseEntity.status(HttpStatus.CREATED).body(payloadTemp);
    }

}

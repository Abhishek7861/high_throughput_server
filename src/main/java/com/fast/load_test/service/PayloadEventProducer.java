package com.fast.load_test.service;

import com.fast.load_test.redis_utils.PayloadStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayloadEventProducer {

    private final StringRedisTemplate redisTemplate;

    /** Queues a new payload; the consumer inserts it with a DB-generated id. Returns the stream entry id. */
    public String publish(String hash) {

        Map<String, String> fields = Map.of("hash", hash);

        RecordId recordId = redisTemplate.opsForStream()
                .add(StreamRecords.mapBacked(fields)
                        .withStreamKey(PayloadStream.KEY));

        if (recordId == null) {
            throw new IllegalStateException(
                    "Failed to publish Redis event");
        }

        return recordId.getValue();
    }

}

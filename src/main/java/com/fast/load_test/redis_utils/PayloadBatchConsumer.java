package com.fast.load_test.redis_utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fast.load_test.redis_utils.PayloadStream.DEAD_LETTER_KEY;
import static com.fast.load_test.redis_utils.PayloadStream.GROUP;
import static com.fast.load_test.redis_utils.PayloadStream.KEY;

/**
 * Drains the payload stream into PostgreSQL in batches on a dedicated thread.
 * <ul>
 *   <li>Records are acknowledged and deleted from the stream only after the DB commit.</li>
 *   <li>Transient DB/Redis failures leave records pending; they're retried after a backoff.</li>
 *   <li>Records that can never be written are moved to the dead-letter stream.</li>
 *   <li>Pending records idle on other (dead) consumers are claimed and retried.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayloadBatchConsumer implements SmartLifecycle {
    private static final int BATCH_SIZE = 500;
    // Non-blocking reads: a blocking XREADGROUP needs a dedicated connection per call and
    // would exceed the 200ms Redis command timeout
    private static final Duration EMPTY_POLL_PAUSE = Duration.ofMillis(50);
    private static final Duration ERROR_BACKOFF = Duration.ofSeconds(1);
    private static final Duration CLAIM_MIN_IDLE = Duration.ofSeconds(30);
    private static final Duration CLAIM_INTERVAL = Duration.ofSeconds(30);

    private static final int MAX_HASH_LENGTH = 255;
    // event_id makes replays (e.g. a crash between DB commit and XACK) a no-op
    private static final String INSERT_SQL = """
            INSERT INTO payloads(created_at, hash_code, event_id)
            VALUES (?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    // Unique per instance so multiple app instances don't share a pending list
    @Value("${payload.stream.consumer-name:${HOSTNAME:consumer-1}}")
    private String consumerName;

    private volatile boolean running;
    private Thread worker;
    private boolean groupReady;
    // Re-read our own pending records first: at startup, after a failure, and after claiming
    private boolean checkPending = true;
    private long nextClaimAt;

    private record Event(MapRecord<String, Object, Object> record, Instant createdAt, String hash) {
        String eventId() {
            return record.getId().getValue();
        }
    }

    @Override
    public void start() {
        running = true;
        worker = Thread.ofPlatform().name("payload-stream-consumer").start(this::run);
        log.info("Payload stream consumer '{}' started", consumerName);
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void run() {
        while (running) {
            try {
                ensureGroup();
                claimStaleRecords();

                List<MapRecord<String, Object, Object>> records = List.of();
                if (checkPending) {
                    records = read(ReadOffset.from("0"));
                    checkPending = !records.isEmpty();
                }
                if (records.isEmpty()) {
                    records = read(ReadOffset.lastConsumed());
                }
                if (records.isEmpty()) {
                    pause(EMPTY_POLL_PAUSE);
                    continue;
                }
                process(records);
            } catch (Exception e) {
                if (!running) {
                    break;
                }
                if (hasCause(e, "NOGROUP")) {
                    groupReady = false;
                }
                checkPending = true;
                log.warn("Payload stream consumer failed, retrying in {}: {}", ERROR_BACKOFF, e.getMessage());
                pause(ERROR_BACKOFF);
            }
        }
    }

    private void ensureGroup() {
        if (groupReady) {
            return;
        }
        try {
            // MKSTREAM so the group can be created before the first event is published
            redisTemplate.execute((RedisCallback<String>) connection -> connection.streamCommands()
                    .xGroupCreate(KEY.getBytes(StandardCharsets.UTF_8), GROUP, ReadOffset.from("0"), true));
        } catch (Exception e) {
            if (!hasCause(e, "BUSYGROUP")) {
                throw e;
            }
        }
        groupReady = true;
    }

    private void claimStaleRecords() {
        long now = System.currentTimeMillis();
        if (now < nextClaimAt) {
            return;
        }
        nextClaimAt = now + CLAIM_INTERVAL.toMillis();

        StreamOperations<String, Object, Object> ops = redisTemplate.opsForStream();
        PendingMessages pending = ops.pending(KEY, GROUP, Range.unbounded(), BATCH_SIZE, CLAIM_MIN_IDLE);
        RecordId[] stale = pending.stream()
                .filter(message -> !consumerName.equals(message.getConsumerName()))
                .map(PendingMessage::getId)
                .toArray(RecordId[]::new);
        if (stale.length > 0) {
            ops.claim(KEY, GROUP, consumerName, CLAIM_MIN_IDLE, stale);
            checkPending = true;
            log.info("Claimed {} stale pending records from other consumers", stale.length);
        }
    }

    private List<MapRecord<String, Object, Object>> read(ReadOffset offset) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(GROUP, consumerName),
                StreamReadOptions.empty().count(BATCH_SIZE),
                StreamOffset.create(KEY, offset));
        return records == null ? List.of() : records;
    }

    private void process(List<MapRecord<String, Object, Object>> records) {
        List<RecordId> done = new ArrayList<>(records.size());
        List<Event> events = new ArrayList<>(records.size());

        for (MapRecord<String, Object, Object> record : records) {
            try {
                events.add(parse(record));
            } catch (RuntimeException e) {
                deadLetter(record, "invalid record: " + e.getMessage());
                done.add(record.getId());
            }
        }

        try {
            insert(events);
            events.forEach(event -> done.add(event.record().getId()));
        } catch (DataAccessException e) {
            if (isTransient(e)) {
                throw e;
            }
            // A record in the batch can't be written; isolate it so the rest still go through
            log.warn("Batch insert failed, retrying records individually: {}", e.getMessage());
            for (Event event : events) {
                try {
                    insert(List.of(event));
                } catch (DataAccessException single) {
                    if (isTransient(single)) {
                        throw single;
                    }
                    deadLetter(event.record(), single.getMessage());
                }
                done.add(event.record().getId());
            }
        } finally {
            complete(done);
        }
    }

    private Event parse(MapRecord<String, Object, Object> record) {
        Object hash = record.getValue().get("hash");
        if (hash == null || hash.toString().isBlank() || hash.toString().length() > MAX_HASH_LENGTH) {
            throw new IllegalArgumentException("hash must contain 1 to " + MAX_HASH_LENGTH + " characters");
        }
        // The stream entry id starts with the publish time in epoch millis
        Instant createdAt = Instant.ofEpochMilli(record.getId().getTimestamp());
        return new Event(record, createdAt, hash.toString());
    }

    private void insert(List<Event> events) {
        if (events.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.batchUpdate(INSERT_SQL, events, events.size(), (ps, event) -> {
                    ps.setTimestamp(1, Timestamp.from(event.createdAt()));
                    ps.setString(2, event.hash());
                    ps.setString(3, event.eventId());
                }));
    }

    private void deadLetter(MapRecord<String, Object, Object> record, String error) {
        Map<String, String> fields = new HashMap<>();
        record.getValue().forEach((k, v) -> fields.put(k.toString(), String.valueOf(v)));
        fields.put("sourceId", record.getId().getValue());
        fields.put("error", String.valueOf(error));
        redisTemplate.opsForStream().add(StreamRecords.mapBacked(fields).withStreamKey(DEAD_LETTER_KEY));
        log.warn("Moved record {} to {}: {}", record.getId(), DEAD_LETTER_KEY, error);
    }

    /** Acknowledge and delete committed (or dead-lettered) records so the stream doesn't grow forever. */
    private void complete(List<RecordId> ids) {
        if (ids.isEmpty()) {
            return;
        }
        RecordId[] idArray = ids.toArray(RecordId[]::new);
        StreamOperations<String, Object, Object> ops = redisTemplate.opsForStream();
        ops.acknowledge(KEY, GROUP, idArray);
        ops.delete(KEY, idArray);
    }

    private static boolean isTransient(DataAccessException e) {
        return e instanceof TransientDataAccessException
                || e instanceof RecoverableDataAccessException
                // Includes CannotGetJdbcConnectionException (DB down or pool exhausted)
                || e instanceof DataAccessResourceFailureException;
    }

    private static boolean hasCause(Throwable e, String text) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}

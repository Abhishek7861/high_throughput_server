package com.fast.load_test.redis_utils;

public final class PayloadStream {
    public static final String KEY = "payload:write-stream";
    public static final String GROUP = "payload-writers";
    // Records that can never be written (bad fields, constraint violations) end up here
    public static final String DEAD_LETTER_KEY = "payload:write-stream:dlq";

    private PayloadStream() {
    }
}

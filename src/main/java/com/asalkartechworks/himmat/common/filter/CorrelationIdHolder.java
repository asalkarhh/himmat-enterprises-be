package com.asalkartechworks.himmat.common.filter;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * ThreadLocal / MDC holder for the correlation request ID.
 */
public final class CorrelationIdHolder {

    public static final String MDC_KEY = "requestId";
    public static final String HEADER_NAME = "X-Request-Id";

    private static final ThreadLocal<String> CURRENT_ID = new ThreadLocal<>();

    private CorrelationIdHolder() {
    }

    public static void set(String requestId) {
        String id = (requestId != null && !requestId.isBlank()) ? requestId.trim() : UUID.randomUUID().toString();
        CURRENT_ID.set(id);
        MDC.put(MDC_KEY, id);
    }

    public static String get() {
        String id = CURRENT_ID.get();
        if (id == null) {
            id = MDC.get(MDC_KEY);
        }
        if (id == null) {
            id = UUID.randomUUID().toString();
            CURRENT_ID.set(id);
            MDC.put(MDC_KEY, id);
        }
        return id;
    }

    public static void clear() {
        CURRENT_ID.remove();
        MDC.remove(MDC_KEY);
    }
}

package com.zimasahealth.zcare.common.context;

import java.util.UUID;

/**
 * The request id and correlation id of the current request thread. The correlation id travels
 * from the caller through audit rows and events to outbound calls (04B section 9).
 */
public final class RequestCorrelation {

    private static final ThreadLocal<Ids> CURRENT = new ThreadLocal<>();

    private RequestCorrelation() {
    }

    public static void set(String requestId, String correlationId) {
        CURRENT.set(new Ids(requestId, correlationId));
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Unique per request; becomes the envelope's {@code requestId}. */
    public static String requestId() {
        Ids ids = CURRENT.get();
        return ids != null ? ids.requestId() : UUID.randomUUID().toString();
    }

    /** Supplied by the caller in {@code X-Correlation-Id}, or generated. */
    public static String correlationId() {
        Ids ids = CURRENT.get();
        return ids != null ? ids.correlationId() : "uncorrelated";
    }

    private record Ids(String requestId, String correlationId) {
    }
}

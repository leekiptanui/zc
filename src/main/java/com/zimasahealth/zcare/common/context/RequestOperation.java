package com.zimasahealth.zcare.common.context;

/**
 * The {@code @AuditOperation} of the endpoint serving the current request. Writes made as a side
 * effect, such as tasks raised when a plan is submitted, are audited under the same operation
 * as the call that caused them, so {@code auditTrail.operation} finds every row it produced.
 */
public final class RequestOperation {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestOperation() {
    }

    public static void set(String operation) {
        CURRENT.set(operation);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** The current request's operation, or the fallback outside a request. */
    public static String currentOr(String fallback) {
        String operation = CURRENT.get();
        return operation != null ? operation : fallback;
    }
}

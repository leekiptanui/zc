package com.zimasahealth.zcare.common.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.common.context.RequestCorrelation;

/**
 * The eight-field ENG-STD-SB-001 envelope every endpoint returns (04B section 5). {@code data} is
 * a single object, never a bare array.
 *
 * <p>Controllers build the business part ({@code status}, {@code data}, {@code exceptions},
 * {@code nextActions}, {@code sessionContext}); {@link EnvelopeAdvice} completes
 * {@code requestId}, {@code timestamp} and {@code auditTrail} on the way out.
 */
@JsonPropertyOrder({"requestId", "timestamp", "status", "data", "exceptions", "nextActions",
        "sessionContext", "auditTrail"})
public final class ApiResponse<T> {

    private String requestId;
    private Instant timestamp;
    private EnvelopeStatus status;
    private T data;
    private final List<ApiException> exceptions = new ArrayList<>();
    private final List<String> nextActions = new ArrayList<>();
    private final Map<String, Object> sessionContext = new LinkedHashMap<>();
    private AuditContext auditTrail;

    private ApiResponse(EnvelopeStatus status, T data) {
        this.status = status;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(EnvelopeStatus.SUCCESS, data);
    }

    public static <T> ApiResponse<T> of(EnvelopeStatus status, T data) {
        return new ApiResponse<>(status, data);
    }

    /** Wraps a service result: its warnings turn the status to {@code exception}. */
    public static <T> ApiResponse<T> from(ServiceResult<T> result) {
        ApiResponse<T> response = success(result.data());
        response.nextActions.addAll(result.nextActions());
        response.sessionContext.putAll(result.session());
        result.warnings().forEach(response::addException);
        return response;
    }

    public ApiResponse<T> nextActions(List<String> actions) {
        nextActions.clear();
        nextActions.addAll(actions);
        return this;
    }

    public ApiResponse<T> nextActions(String... actions) {
        return nextActions(List.of(actions));
    }

    public ApiResponse<T> session(String key, Object value) {
        if (value != null) {
            sessionContext.put(key, value);
        }
        return this;
    }

    public ApiResponse<T> session(Map<String, Object> values) {
        values.forEach(this::session);
        return this;
    }

    /** Adds a business exception; a success becomes an {@code exception} envelope. */
    public ApiResponse<T> addException(ApiException exception) {
        exceptions.add(exception);
        if (status == EnvelopeStatus.SUCCESS) {
            status = EnvelopeStatus.EXCEPTION;
        }
        return this;
    }

    /** Fills the request-level fields that the controller does not know. */
    public void complete(String operation) {
        if (requestId == null) {
            requestId = RequestCorrelation.requestId();
        }
        if (timestamp == null) {
            timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        }
        String correlationId = RequestCorrelation.correlationId();
        sessionContext.putIfAbsent("correlationId", correlationId);
        if (auditTrail == null) {
            auditTrail = new AuditContext(operation, CurrentActor.current().map(a -> a.id()).orElse(null),
                    correlationId);
        }
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public EnvelopeStatus getStatus() {
        return status;
    }

    public T getData() {
        return data;
    }

    public List<ApiException> getExceptions() {
        return exceptions;
    }

    public List<String> getNextActions() {
        return nextActions;
    }

    public Map<String, Object> getSessionContext() {
        return sessionContext;
    }

    public AuditContext getAuditTrail() {
        return auditTrail;
    }
}

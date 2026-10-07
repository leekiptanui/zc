package com.zimasahealth.zcare.common.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A business rule refused the request. Thrown from services, it rolls the transaction back and
 * becomes an HTTP 200 envelope with {@code status: exception} (04B section 6). Never used for
 * authentication or authorisation, which are 401 and 403 outside the envelope.
 */
public class BusinessException extends RuntimeException {

    private final ZCareExceptionCode code;
    private String field;
    private final Map<String, Object> context = new LinkedHashMap<>();
    private final Map<String, Object> session = new LinkedHashMap<>();
    private Object data;
    private List<String> nextActions;

    public BusinessException(ZCareExceptionCode code, String message) {
        // Business outcomes are expected; a stack trace would only cost time.
        super(message, null, false, false);
        this.code = code;
    }

    public static BusinessException of(ZCareExceptionCode code, String message) {
        return new BusinessException(code, message);
    }

    /** VALIDATION_FIELD_INVALID on one field; also used for ids unknown in this tenant. */
    public static BusinessException invalid(String field, String message) {
        return new BusinessException(ZCareExceptionCode.VALIDATION_FIELD_INVALID, message).field(field);
    }

    /** VALIDATION_FIELD_REQUIRED on one field. */
    public static BusinessException required(String field, String message) {
        return new BusinessException(ZCareExceptionCode.VALIDATION_FIELD_REQUIRED, message).field(field);
    }

    /** The id in {@code field} names no record visible to this tenant. */
    public static BusinessException unknown(String field, String what) {
        return invalid(field, "Unknown " + what + " for this tenant");
    }

    public BusinessException field(String field) {
        this.field = field;
        return this;
    }

    public BusinessException with(String key, Object value) {
        if (value != null) {
            context.put(key, value);
        }
        return this;
    }

    /** Data returned alongside the exception, such as the record's current state. */
    public BusinessException data(Object data) {
        this.data = data;
        return this;
    }

    public BusinessException session(String key, Object value) {
        if (value != null) {
            session.put(key, value);
        }
        return this;
    }

    /** Overrides the code's default allow-list. */
    public BusinessException nextActions(String... actions) {
        this.nextActions = List.of(actions);
        return this;
    }

    public ZCareExceptionCode code() {
        return code;
    }

    public String field() {
        return field;
    }

    public Map<String, Object> context() {
        return Map.copyOf(context);
    }

    public Map<String, Object> session() {
        return Map.copyOf(session);
    }

    public Object data() {
        return data;
    }

    public List<String> effectiveNextActions() {
        return nextActions != null ? nextActions : code.nextActions();
    }
}

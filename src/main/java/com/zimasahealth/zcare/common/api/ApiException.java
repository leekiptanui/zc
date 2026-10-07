package com.zimasahealth.zcare.common.api;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zimasahealth.zcare.common.error.ZCareExceptionCode;

/**
 * One typed business exception inside the envelope (04B section 6). Not a Java exception: it is
 * the wire shape {@code {code, severity, message, field?, escalateTo?, context}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiException(
        String code,
        Severity severity,
        String message,
        String field,
        String escalateTo,
        Map<String, Object> context) {

    public ApiException {
        context = context == null ? Map.of() : context;
    }

    public static ApiException of(ZCareExceptionCode code, String message, String field, Map<String, Object> context) {
        return new ApiException(code.code(), code.severity(), message, field, code.escalateTo(), context);
    }

    public static ApiException of(ZCareExceptionCode code, String message) {
        return of(code, message, null, Map.of());
    }
}

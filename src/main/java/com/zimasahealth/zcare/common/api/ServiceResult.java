package com.zimasahealth.zcare.common.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.zimasahealth.zcare.common.error.ZCareExceptionCode;

/**
 * What a service returns to its controller: the DTO, the business decision about what the
 * caller may do next, the identifiers to carry forward, and any non-fatal business exceptions.
 *
 * <p>A warning differs from a thrown {@link com.zimasahealth.zcare.common.error.BusinessException}:
 * the transaction commits and the work is kept, but the envelope still reports
 * {@code status: exception}. An outreach held by the frequency limit is the canonical case.
 */
public final class ServiceResult<T> {

    private final T data;
    private final List<String> nextActions = new ArrayList<>();
    private final Map<String, Object> session = new LinkedHashMap<>();
    private final List<ApiException> warnings = new ArrayList<>();

    private ServiceResult(T data) {
        this.data = data;
    }

    public static <T> ServiceResult<T> of(T data) {
        return new ServiceResult<>(data);
    }

    public ServiceResult<T> next(String... actions) {
        nextActions.clear();
        nextActions.addAll(List.of(actions));
        return this;
    }

    public ServiceResult<T> session(String key, Object value) {
        if (value != null) {
            session.put(key, value);
        }
        return this;
    }

    /** Records a non-fatal business exception; its code's next actions replace the current ones. */
    public ServiceResult<T> warning(ZCareExceptionCode code, String message, Map<String, Object> context) {
        warnings.add(ApiException.of(code, message, null, context));
        nextActions.clear();
        nextActions.addAll(code.nextActions());
        return this;
    }

    public T data() {
        return data;
    }

    public List<String> nextActions() {
        return List.copyOf(nextActions);
    }

    public Map<String, Object> session() {
        return Map.copyOf(session);
    }

    public List<ApiException> warnings() {
        return List.copyOf(warnings);
    }
}

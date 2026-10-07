package com.zimasahealth.zcare.common.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zimasahealth.zcare.common.api.ApiException;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.common.api.EnvelopeStatus;
import com.zimasahealth.zcare.common.context.RequestCorrelation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns failures into envelopes (04B section 6). Business rules and validation ride HTTP 200 with
 * {@code status: exception}; validation yields one entry per failed field and never a 422.
 * Infrastructure failures are HTTP 500 with {@code status: error}. Authentication and
 * authorisation never reach here: the security layer answers them with 401 and 403.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Set<String> REQUIRED_CONSTRAINTS = Set.of("NotNull", "NotBlank", "NotEmpty");

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Object> business(BusinessException ex, HandlerMethod handler) {
        ApiResponse<Object> response = ApiResponse.of(EnvelopeStatus.EXCEPTION, ex.data())
                .addException(ApiException.of(ex.code(), ex.getMessage(), ex.field(), ex.context()))
                .nextActions(ex.effectiveNextActions())
                .session(ex.session());
        response.complete(operation(handler));
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Object> invalidBody(MethodArgumentNotValidException ex, HandlerMethod handler) {
        List<ApiException> entries = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            entries.add(fieldEntry(error.getField(), error.getCode(), error.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                entries.add(fieldEntry(error.getObjectName(), error.getCode(), error.getDefaultMessage())));
        return validation(entries, handler);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ApiResponse<Object> invalidParameters(HandlerMethodValidationException ex, HandlerMethod handler) {
        List<ApiException> entries = new ArrayList<>();
        ex.getAllValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> entries.add(fieldEntry(name,
                    error.getCodes() != null && error.getCodes().length > 0 ? lastSegment(error.getCodes()[0]) : null,
                    error.getDefaultMessage())));
        });
        return validation(entries, handler);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Object> invalidConstraints(ConstraintViolationException ex, HandlerMethod handler) {
        List<ApiException> entries = ex.getConstraintViolations().stream()
                .map(v -> fieldEntry(lastSegment(v.getPropertyPath().toString()),
                        v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        v.getMessage()))
                .toList();
        return validation(entries, handler);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Object> unreadableBody(HttpMessageNotReadableException ex, HandlerMethod handler) {
        return validation(List.of(ApiException.of(ZCareExceptionCode.VALIDATION_FIELD_INVALID,
                "The request body is missing or is not valid JSON for this operation", "body", Map.of())), handler);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Object> typeMismatch(MethodArgumentTypeMismatchException ex, HandlerMethod handler) {
        return validation(List.of(ApiException.of(ZCareExceptionCode.VALIDATION_FIELD_INVALID,
                "'" + ex.getValue() + "' is not a valid value for " + ex.getName(), ex.getName(), Map.of())), handler);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Object> missingParameter(MissingServletRequestParameterException ex, HandlerMethod handler) {
        return validation(List.of(ApiException.of(ZCareExceptionCode.VALIDATION_FIELD_REQUIRED,
                ex.getParameterName() + " is required", ex.getParameterName(), Map.of())), handler);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ApiResponse<Object> missingHeader(MissingRequestHeaderException ex, HandlerMethod handler) {
        return validation(List.of(ApiException.of(ZCareExceptionCode.VALIDATION_FIELD_REQUIRED,
                "Header " + ex.getHeaderName() + " is required", ex.getHeaderName(), Map.of())), handler);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResponse<Object> integrity(DataIntegrityViolationException ex, HandlerMethod handler) {
        log.info("Database constraint refused a write: {}",
                ConstraintViolationTranslator.constraintName(ex).orElse("unnamed"));
        return business(ConstraintViolationTranslator.translate(ex), handler);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ApiResponse<Object> concurrent(OptimisticLockingFailureException ex, HandlerMethod handler) {
        return business(BusinessException.of(ZCareExceptionCode.ZCARE_CONCURRENT_MODIFICATION,
                "The record was changed by another request; reload it and try again"), handler);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception ex, HandlerMethod handler) throws Exception {
        if (ex instanceof ErrorResponse errorResponse) {
            // Transport-level refusals (415, 406 ...) keep Spring's status, outside the envelope.
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .body(Map.of("error", String.valueOf(errorResponse.getBody().getDetail())));
        }
        log.error("Unhandled error, correlationId={}", RequestCorrelation.correlationId(), ex);
        ApiResponse<Object> response = ApiResponse.of(EnvelopeStatus.ERROR, null)
                .addException(ApiException.of(ZCareExceptionCode.ZCARE_INTERNAL_ERROR,
                        "An unexpected error occurred; quote the correlation id when reporting it"))
                .nextActions(List.of());
        response.complete(operation(handler));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private ApiResponse<Object> validation(List<ApiException> entries, HandlerMethod handler) {
        ApiResponse<Object> response = ApiResponse.of(EnvelopeStatus.EXCEPTION, null);
        entries.forEach(response::addException);
        response.nextActions("retry");
        response.complete(operation(handler));
        return response;
    }

    private static ApiException fieldEntry(String field, String constraint, String message) {
        ZCareExceptionCode code = constraint != null && REQUIRED_CONSTRAINTS.contains(constraint)
                ? ZCareExceptionCode.VALIDATION_FIELD_REQUIRED
                : ZCareExceptionCode.VALIDATION_FIELD_INVALID;
        return ApiException.of(code, field + ": " + message, field, Map.of());
    }

    private static String lastSegment(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private static String operation(HandlerMethod handler) {
        if (handler == null) {
            return "UNSPECIFIED";
        }
        AuditOperation operation = handler.getMethodAnnotation(AuditOperation.class);
        return operation != null ? operation.value() : "UNSPECIFIED";
    }
}

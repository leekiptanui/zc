package com.zimasahealth.zcare.common.api;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Completes every outgoing envelope: request id, timestamp, correlation id, and an
 * {@code auditTrail} whose operation comes from the endpoint's {@link AuditOperation}.
 */
@RestControllerAdvice
public class EnvelopeAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Decided per body below: an envelope may arrive bare or inside a ResponseEntity.
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> envelope) {
            AuditOperation operation = returnType.getMethodAnnotation(AuditOperation.class);
            envelope.complete(operation != null ? operation.value() : "UNSPECIFIED");
        }
        return body;
    }
}

package com.zimasahealth.zcare.common.api;

import com.zimasahealth.zcare.common.context.RequestOperation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Publishes the handler's {@link AuditOperation} as the request's {@link RequestOperation}. */
public class AuditOperationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            AuditOperation operation = method.getMethodAnnotation(AuditOperation.class);
            if (operation != null) {
                RequestOperation.set(operation.value());
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        RequestOperation.clear();
    }
}

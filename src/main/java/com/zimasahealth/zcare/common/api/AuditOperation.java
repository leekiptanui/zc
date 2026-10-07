package com.zimasahealth.zcare.common.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names the operation an endpoint performs. It fills {@code auditTrail.operation} on every
 * envelope the endpoint returns, success or exception, and must equal the operation the service
 * writes to {@code zc_domain_audit}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditOperation {

    String value();
}

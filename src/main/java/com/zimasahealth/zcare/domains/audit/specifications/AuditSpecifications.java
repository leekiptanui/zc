package com.zimasahealth.zcare.domains.audit.specifications;

import java.time.Instant;

import com.zimasahealth.zcare.domains.audit.entities.DomainAuditRecord;
import org.springframework.data.jpa.domain.Specification;

/** Audit filters: who, whom, what, the thread, and when. A null argument means "no filter". */
public final class AuditSpecifications {

    private AuditSpecifications() {
    }

    public static Specification<DomainAuditRecord> equalTo(String attribute, Object value) {
        return (root, query, cb) -> value == null ? null : cb.equal(root.get(attribute), value);
    }

    public static Specification<DomainAuditRecord> occurredFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("occurredAt"), from);
    }

    public static Specification<DomainAuditRecord> occurredBefore(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThan(root.get("occurredAt"), to);
    }
}

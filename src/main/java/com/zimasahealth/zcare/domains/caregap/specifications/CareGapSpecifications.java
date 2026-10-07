package com.zimasahealth.zcare.domains.caregap.specifications;

import com.zimasahealth.zcare.domains.caregap.entities.CareGap;
import org.springframework.data.jpa.domain.Specification;

/** Search filters for care gaps. A null argument means "no filter". */
public final class CareGapSpecifications {

    private CareGapSpecifications() {
    }

    public static Specification<CareGap> hasStatus(String status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<CareGap> forEnrolment(Long enrolmentId) {
        return (root, query, cb) -> enrolmentId == null ? null : cb.equal(root.get("enrolmentId"), enrolmentId);
    }

    public static Specification<CareGap> ofType(String gapType) {
        return (root, query, cb) -> gapType == null ? null : cb.equal(root.get("gapType"), gapType);
    }
}

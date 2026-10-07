package com.zimasahealth.zcare.domains.enrolment.specifications;

import com.zimasahealth.zcare.domains.enrolment.entities.Enrolment;
import org.springframework.data.jpa.domain.Specification;

/** Search filters for enrolments. A null argument means "no filter". */
public final class EnrolmentSpecifications {

    private EnrolmentSpecifications() {
    }

    public static Specification<Enrolment> hasStatus(String status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Enrolment> inProgramme(Long programmeId) {
        return (root, query, cb) -> programmeId == null ? null : cb.equal(root.get("programmeId"), programmeId);
    }

    public static Specification<Enrolment> forMember(Long memberId) {
        return (root, query, cb) -> memberId == null ? null : cb.equal(root.get("memberId"), memberId);
    }

    public static Specification<Enrolment> responsibleCm(String careManagerId) {
        return (root, query, cb) -> careManagerId == null ? null : cb.equal(root.get("responsibleCm"), careManagerId);
    }
}

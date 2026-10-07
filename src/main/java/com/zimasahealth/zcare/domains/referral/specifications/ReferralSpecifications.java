package com.zimasahealth.zcare.domains.referral.specifications;

import com.zimasahealth.zcare.domains.referral.entities.Referral;
import org.springframework.data.jpa.domain.Specification;

/** Search filters for referrals. A null argument means "no filter". */
public final class ReferralSpecifications {

    private ReferralSpecifications() {
    }

    public static Specification<Referral> hasStatus(String status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Referral> forEnrolment(Long enrolmentId) {
        return (root, query, cb) -> enrolmentId == null ? null : cb.equal(root.get("enrolmentId"), enrolmentId);
    }

    public static Specification<Referral> receivedBy(Long organisationId) {
        return (root, query, cb) -> organisationId == null ? null
                : cb.equal(root.get("receivingOrgId"), organisationId);
    }
}

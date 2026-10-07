package com.zimasahealth.zcare.domains.programme.specifications;

import com.zimasahealth.zcare.domains.programme.entities.Programme;
import org.springframework.data.jpa.domain.Specification;

/** Search filters for programmes. A null argument means "no filter". */
public final class ProgrammeSpecifications {

    private ProgrammeSpecifications() {
    }

    public static Specification<Programme> hasStatus(String status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
}

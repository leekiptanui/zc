package com.zimasahealth.zcare.domains.cohort.repositories;

import java.util.Optional;

import com.zimasahealth.zcare.domains.cohort.entities.CohortMembership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CohortMembershipRepository extends JpaRepository<CohortMembership, Long> {

    Optional<CohortMembership> findByCohortIdAndMemberId(Long cohortId, Long memberId);

    long countByCohortIdAndStatus(Long cohortId, String status);

    Page<CohortMembership> findByCohortId(Long cohortId, Pageable pageable);
}

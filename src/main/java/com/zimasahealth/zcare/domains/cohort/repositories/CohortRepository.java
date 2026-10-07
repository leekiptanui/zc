package com.zimasahealth.zcare.domains.cohort.repositories;

import com.zimasahealth.zcare.domains.cohort.entities.Cohort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CohortRepository extends JpaRepository<Cohort, Long>, JpaSpecificationExecutor<Cohort> {

    boolean existsByProgrammeVersionIdAndCode(Long programmeVersionId, String code);
}

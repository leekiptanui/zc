package com.zimasahealth.zcare.domains.careplan.repositories;

import java.util.Collection;
import java.util.Optional;

import com.zimasahealth.zcare.domains.careplan.entities.CarePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CarePlanRepository extends JpaRepository<CarePlan, Long> {

    Optional<CarePlan> findFirstByEnrolmentIdAndStatusIn(Long enrolmentId, Collection<String> statuses);

    Optional<CarePlan> findFirstByEnrolmentIdAndStatus(Long enrolmentId, String status);

    @Query("SELECT coalesce(max(p.versionNumber), 0) FROM CarePlan p WHERE p.enrolmentId = :enrolmentId")
    int maxVersionNumber(Long enrolmentId);

    long countByProgrammeVersionIdInAndStatus(Collection<Long> programmeVersionIds, String status);
}

package com.zimasahealth.zcare.domains.caregap.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.domains.caregap.entities.CareGap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CareGapRepository extends JpaRepository<CareGap, Long>, JpaSpecificationExecutor<CareGap> {

    Optional<CareGap> findFirstByGapKeyAndStatusIn(String gapKey, Collection<String> statuses);

    List<CareGap> findByEnrolmentIdAndStatusIn(Long enrolmentId, Collection<String> statuses);

    long countByProgrammeVersionIdInAndStatusIn(Collection<Long> programmeVersionIds, Collection<String> statuses);
}

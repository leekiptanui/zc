package com.zimasahealth.zcare.domains.programme.repositories;

import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.domains.programme.entities.GapRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GapRuleRepository extends JpaRepository<GapRule, Long> {

    List<GapRule> findByProgrammeVersionIdOrderByGapTypeAscRuleVersionDesc(Long programmeVersionId);

    List<GapRule> findByProgrammeVersionIdAndActiveTrue(Long programmeVersionId);

    Optional<GapRule> findFirstByProgrammeVersionIdAndGapTypeOrderByRuleVersionDesc(Long programmeVersionId,
                                                                                    String gapType);

    Optional<GapRule> findFirstByProgrammeVersionIdAndGapTypeAndActiveTrueOrderByRuleVersionDesc(
            Long programmeVersionId, String gapType);
}

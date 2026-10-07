package com.zimasahealth.zcare.domains.programme.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.domains.programme.entities.OutcomeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutcomeDefinitionRepository extends JpaRepository<OutcomeDefinition, Long> {

    List<OutcomeDefinition> findByProgrammeVersionIdOrderByMeasureCodeAscMeasureVersionDesc(Long programmeVersionId);

    List<OutcomeDefinition> findByProgrammeVersionIdIn(Collection<Long> programmeVersionIds);

    Optional<OutcomeDefinition> findFirstByProgrammeVersionIdAndMeasureCodeOrderByMeasureVersionDesc(
            Long programmeVersionId, String measureCode);
}

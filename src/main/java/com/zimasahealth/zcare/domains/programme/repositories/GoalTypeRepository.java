package com.zimasahealth.zcare.domains.programme.repositories;

import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.domains.programme.entities.GoalType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalTypeRepository extends JpaRepository<GoalType, Long> {

    List<GoalType> findByProgrammeVersionIdOrderByCode(Long programmeVersionId);

    Optional<GoalType> findByProgrammeVersionIdAndCode(Long programmeVersionId, String code);
}

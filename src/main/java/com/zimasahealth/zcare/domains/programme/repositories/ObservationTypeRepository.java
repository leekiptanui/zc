package com.zimasahealth.zcare.domains.programme.repositories;

import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.domains.programme.entities.ObservationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservationTypeRepository extends JpaRepository<ObservationType, Long> {

    List<ObservationType> findByProgrammeVersionIdOrderByCode(Long programmeVersionId);

    Optional<ObservationType> findByProgrammeVersionIdAndCode(Long programmeVersionId, String code);
}

package com.zimasahealth.zcare.domains.observation.repositories;

import java.util.List;

import com.zimasahealth.zcare.domains.observation.entities.Observation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservationRepository extends JpaRepository<Observation, Long> {

    List<Observation> findByEnrolmentIdOrderByObservedAtAsc(Long enrolmentId);

    List<Observation> findByEnrolmentIdAndObservationTypeIdOrderByObservedAtAsc(Long enrolmentId,
                                                                                Long observationTypeId);

    List<Observation> findTop10ByEnrolmentIdAndInvalidatedFalseOrderByObservedAtDesc(Long enrolmentId);
}

package com.zimasahealth.zcare.domains.outcome.repositories;

import java.util.Collection;
import java.util.List;

import com.zimasahealth.zcare.domains.outcome.entities.OutcomeObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutcomeObservationRepository extends JpaRepository<OutcomeObservation, Long> {

    /** Per measure definition: contributing observations and their mean, value-based only. */
    @Query("""
            SELECT o.outcomeDefinitionId AS definitionId, count(o) AS n, avg(o.valueNumeric) AS mean
              FROM OutcomeObservation o
             WHERE o.outcomeDefinitionId IN :definitionIds AND o.valueNumeric IS NOT NULL
             GROUP BY o.outcomeDefinitionId
            """)
    List<MeasureAggregate> aggregate(Collection<Long> definitionIds);

    interface MeasureAggregate {

        Long getDefinitionId();

        Long getN();

        Double getMean();
    }
}

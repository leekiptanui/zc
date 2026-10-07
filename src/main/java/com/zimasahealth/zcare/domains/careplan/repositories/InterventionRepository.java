package com.zimasahealth.zcare.domains.careplan.repositories;

import java.util.List;

import com.zimasahealth.zcare.domains.careplan.entities.Intervention;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterventionRepository extends JpaRepository<Intervention, Long> {

    List<Intervention> findByCarePlanIdOrderById(Long carePlanId);
}

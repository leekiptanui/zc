package com.zimasahealth.zcare.domains.careplan.repositories;

import java.util.List;

import com.zimasahealth.zcare.domains.careplan.entities.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByCarePlanIdOrderById(Long carePlanId);

    long countByCarePlanId(Long carePlanId);
}

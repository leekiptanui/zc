package com.zimasahealth.zcare.domains.careplan.repositories;

import java.util.List;

import com.zimasahealth.zcare.domains.careplan.entities.PlanReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanReviewRepository extends JpaRepository<PlanReview, Long> {

    List<PlanReview> findByCarePlanIdOrderByReviewedAtAsc(Long carePlanId);
}

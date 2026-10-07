package com.zimasahealth.zcare.domains.careplan.mappers;

import java.util.List;

import com.zimasahealth.zcare.domains.careplan.dto.CarePlanView;
import com.zimasahealth.zcare.domains.careplan.entities.CarePlan;
import com.zimasahealth.zcare.domains.careplan.entities.Goal;
import com.zimasahealth.zcare.domains.careplan.entities.Intervention;
import com.zimasahealth.zcare.domains.careplan.entities.PlanReview;
import org.mapstruct.Mapper;

@Mapper
public interface CarePlanMapper {

    CarePlanView toView(CarePlan plan, List<CarePlanView.GoalView> goals,
                        List<CarePlanView.InterventionView> interventions, List<CarePlanView.ReviewView> reviews);

    CarePlanView.GoalView toView(Goal goal);

    CarePlanView.InterventionView toView(Intervention intervention);

    CarePlanView.ReviewView toView(PlanReview review);
}

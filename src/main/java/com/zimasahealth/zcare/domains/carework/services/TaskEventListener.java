package com.zimasahealth.zcare.domains.carework.services;

import java.time.Duration;

import com.zimasahealth.zcare.common.context.ZcareRole;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.careplan.dto.CarePlanActivatedEvent;
import com.zimasahealth.zcare.domains.careplan.dto.CarePlanDecidedEvent;
import com.zimasahealth.zcare.domains.careplan.dto.CarePlanSubmittedEvent;
import com.zimasahealth.zcare.domains.carework.dto.SystemTask;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentExitedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Work that other domains' decisions create or close. Listeners run synchronously inside the
 * publishing transaction, so the decision and its work commit or roll back together.
 */
@Component
public class TaskEventListener {

    /** SLA values are still unset (OD-29); these defaults stand until they are agreed. */
    static final Duration PLAN_APPROVAL_SLA = Duration.ofHours(72);
    static final Duration SAFE_EXIT_SLA = Duration.ofHours(48);

    private final TaskService tasks;

    public TaskEventListener(TaskService tasks) {
        this.tasks = tasks;
    }

    /** The clinician's approval queue is the task queue (04C S-13). */
    @EventListener
    public void onPlanSubmitted(CarePlanSubmittedEvent event) {
        tasks.raise(new SystemTask(event.enrolmentId(), event.memberId(), TaskTypes.PLAN_APPROVAL,
                "Approve care plan version " + event.versionNumber(), (short) 2, ZcareRole.CLINICIAN.code(),
                Times.now().plus(PLAN_APPROVAL_SLA), "care_plan", event.carePlanId()));
    }

    @EventListener
    public void onPlanDecided(CarePlanDecidedEvent event) {
        tasks.completeByOrigin("care_plan", event.carePlanId(), TaskTypes.PLAN_APPROVAL, event.outcome());
    }

    /** Each intervention of an approved plan becomes a task for its owner role (ZCR-WRK-002). */
    @EventListener
    public void onPlanActivated(CarePlanActivatedEvent event) {
        for (CarePlanActivatedEvent.ActivatedIntervention intervention : event.interventions()) {
            tasks.raise(new SystemTask(event.enrolmentId(), event.memberId(), TaskTypes.CARE_PLAN_INTERVENTION,
                    intervention.description(), (short) 3, intervention.ownerRole(), null, "intervention",
                    intervention.interventionId()));
        }
    }

    /**
     * No open work outlives the enrolment. A member's own request to leave also raises the
     * mandatory, non-persuasive safe-exit follow-up for their care manager (UC-ZC-014).
     */
    @EventListener
    public void onEnrolmentExited(EnrolmentExitedEvent event) {
        tasks.cancelOpen(event.enrolmentId(), event.cancelReason());
        if ("member_request".equals(event.exitReason())) {
            tasks.raiseFor(new SystemTask(event.enrolmentId(), event.memberId(), TaskTypes.SAFE_EXIT,
                    "Safe-exit follow-up", (short) 1, ZcareRole.CARE_MANAGER.code(),
                    Times.now().plus(SAFE_EXIT_SLA), "manual", null), event.responsibleCm());
        }
    }
}

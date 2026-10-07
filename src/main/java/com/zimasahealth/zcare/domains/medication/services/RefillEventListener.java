package com.zimasahealth.zcare.domains.medication.services;

import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentExitedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Stops refill chasing when the enrolment ends, inside the same transaction. */
@Component
public class RefillEventListener {

    private final RefillService refills;

    public RefillEventListener(RefillService refills) {
        this.refills = refills;
    }

    @EventListener
    public void onEnrolmentExited(EnrolmentExitedEvent event) {
        refills.cancelOpen(event.enrolmentId(), event.cancelReason());
    }
}

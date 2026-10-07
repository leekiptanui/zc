package com.zimasahealth.zcare.domains.engagement.services;

import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentExitedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Cancels pending outreach when the enrolment ends, inside the same transaction. */
@Component
public class OutreachEventListener {

    private final OutreachService outreach;

    public OutreachEventListener(OutreachService outreach) {
        this.outreach = outreach;
    }

    @EventListener
    public void onEnrolmentExited(EnrolmentExitedEvent event) {
        outreach.cancelPending(event.enrolmentId(), event.cancelReason());
    }
}

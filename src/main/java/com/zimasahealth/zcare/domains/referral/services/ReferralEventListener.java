package com.zimasahealth.zcare.domains.referral.services;

import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentExitedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Closes open referrals when their enrolment ends, inside the same transaction. */
@Component
public class ReferralEventListener {

    private final ReferralService referrals;

    public ReferralEventListener(ReferralService referrals) {
        this.referrals = referrals;
    }

    @EventListener
    public void onEnrolmentExited(EnrolmentExitedEvent event) {
        referrals.cancelOpen(event.enrolmentId(), event.cancelReason());
    }
}

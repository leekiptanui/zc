package com.zimasahealth.zcare.domains.caregap.services;

import com.zimasahealth.zcare.common.carecontext.CareContextContributor;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentExitedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** The enrolment's open gaps in its care context, and the closing of them when it ends. */
@Component
public class CareGapSupport implements CareContextContributor {

    private final CareGapService gaps;

    public CareGapSupport(CareGapService gaps) {
        this.gaps = gaps;
    }

    @Override
    public String section() {
        return "openCareGaps";
    }

    @Override
    public Object contribute(long enrolmentId) {
        return gaps.openForEnrolment(enrolmentId);
    }

    @EventListener
    public void onEnrolmentExited(EnrolmentExitedEvent event) {
        gaps.closeOpen(event.enrolmentId(), event.cancelReason());
    }
}

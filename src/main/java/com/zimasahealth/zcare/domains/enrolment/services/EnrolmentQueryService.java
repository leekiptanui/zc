package com.zimasahealth.zcare.domains.enrolment.services;

import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.common.api.PageParams;
import com.zimasahealth.zcare.common.api.PagedResponse;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentCounts;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.entities.Enrolment;
import com.zimasahealth.zcare.domains.enrolment.mappers.EnrolmentMapper;
import com.zimasahealth.zcare.domains.enrolment.repositories.ConsentRecordRepository;
import com.zimasahealth.zcare.domains.enrolment.repositories.EnrolmentRepository;
import com.zimasahealth.zcare.domains.enrolment.specifications.EnrolmentSpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enrolments as other domains and the list endpoint see them. */
@Service
@Transactional(readOnly = true)
public class EnrolmentQueryService {

    private final EnrolmentRepository enrolments;
    private final ConsentRecordRepository consents;
    private final EnrolmentMapper mapper;

    public EnrolmentQueryService(EnrolmentRepository enrolments, ConsentRecordRepository consents,
                                 EnrolmentMapper mapper) {
        this.enrolments = enrolments;
        this.consents = consents;
        this.mapper = mapper;
    }

    public PagedResponse<EnrolmentView> list(String status, Long programmeId, Long memberId, String responsibleCm,
                                             PageParams params) {
        Specification<Enrolment> filter = Specification.where(EnrolmentSpecifications.hasStatus(status))
                .and(EnrolmentSpecifications.inProgramme(programmeId))
                .and(EnrolmentSpecifications.forMember(memberId))
                .and(EnrolmentSpecifications.responsibleCm(responsibleCm));
        return PagedResponse.of(enrolments.findAll(filter,
                params.toPageable(Sort.by(Sort.Direction.DESC, "invitedAt"))), params, mapper::toView);
    }

    /** The enrolment, which must exist in this tenant; an unknown id is reported on {@code field}. */
    public EnrolmentView require(long enrolmentId, String field) {
        return enrolments.findById(enrolmentId).map(mapper::toView)
                .orElseThrow(() -> BusinessException.unknown(field, "enrolment"));
    }

    /** The enrolment, which must also be active: care work happens only on active enrolments. */
    public EnrolmentView requireActive(long enrolmentId, String field) {
        EnrolmentView enrolment = require(enrolmentId, field);
        if (!enrolment.isActive()) {
            throw BusinessException.invalid(field, "Enrolment " + enrolmentId + " is " + enrolment.status()
                    + "; this needs an active enrolment").with("enrolmentState", enrolment.status());
        }
        return enrolment;
    }

    /** The member's live enrolment, newest first, or failing that their latest. */
    public Optional<EnrolmentView> currentForMember(long memberId) {
        return enrolments.findFirstByMemberIdAndStatusInOrderByInvitedAtDesc(memberId, EnrolmentService.LIVE)
                .or(() -> enrolments.findFirstByMemberIdOrderByInvitedAtDesc(memberId))
                .map(mapper::toView);
    }

    public EnrolmentCounts countsForProgramme(long programmeId) {
        return new EnrolmentCounts(
                enrolments.countByProgrammeId(programmeId),
                consents.countConsentedInProgramme(programmeId),
                enrolments.countByProgrammeIdAndStatusIn(programmeId, List.of("active", "completed")),
                enrolments.countByProgrammeIdAndStatusIn(programmeId, List.of("withdrawn")));
    }
}

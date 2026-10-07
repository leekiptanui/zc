package com.zimasahealth.zcare.domains.enrolment.repositories;

import java.util.Collection;
import java.util.Optional;

import com.zimasahealth.zcare.domains.enrolment.entities.Enrolment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EnrolmentRepository extends JpaRepository<Enrolment, Long>, JpaSpecificationExecutor<Enrolment> {

    Optional<Enrolment> findFirstByMemberIdAndProgrammeIdAndStatusIn(Long memberId, Long programmeId,
                                                                    Collection<String> statuses);

    Optional<Enrolment> findFirstByMemberIdAndStatusInOrderByInvitedAtDesc(Long memberId, Collection<String> statuses);

    Optional<Enrolment> findFirstByMemberIdOrderByInvitedAtDesc(Long memberId);

    long countByProgrammeId(Long programmeId);

    long countByProgrammeIdAndStatusIn(Long programmeId, Collection<String> statuses);
}

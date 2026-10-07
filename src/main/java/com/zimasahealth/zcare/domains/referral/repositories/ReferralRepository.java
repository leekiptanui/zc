package com.zimasahealth.zcare.domains.referral.repositories;

import java.util.Collection;
import java.util.List;

import com.zimasahealth.zcare.domains.referral.entities.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReferralRepository extends JpaRepository<Referral, Long>, JpaSpecificationExecutor<Referral> {

    List<Referral> findByEnrolmentIdAndStatusIn(Long enrolmentId, Collection<String> statuses);
}

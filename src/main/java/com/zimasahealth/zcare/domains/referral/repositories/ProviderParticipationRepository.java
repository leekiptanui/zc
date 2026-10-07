package com.zimasahealth.zcare.domains.referral.repositories;

import java.util.Collection;
import java.util.Optional;

import com.zimasahealth.zcare.domains.referral.entities.ProviderParticipation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderParticipationRepository extends JpaRepository<ProviderParticipation, Long> {

    Optional<ProviderParticipation> findFirstByProgrammeVersionIdAndOrganisationIdAndStatusIn(
            Long programmeVersionId, Long organisationId, Collection<String> statuses);
}

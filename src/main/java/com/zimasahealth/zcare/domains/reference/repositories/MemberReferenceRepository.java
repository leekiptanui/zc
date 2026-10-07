package com.zimasahealth.zcare.domains.reference.repositories;

import java.util.Optional;

import com.zimasahealth.zcare.domains.reference.entities.MemberReference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberReferenceRepository extends JpaRepository<MemberReference, Long> {

    Optional<MemberReference> findBySourceSystemAndSourceMemberNumberAndSourceIndividualRef(
            String sourceSystem, String sourceMemberNumber, String sourceIndividualRef);
}

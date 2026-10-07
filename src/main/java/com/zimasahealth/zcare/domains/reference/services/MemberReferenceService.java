package com.zimasahealth.zcare.domains.reference.services;

import java.time.LocalDate;
import java.util.Optional;

import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.domains.reference.dto.MemberIdentity;
import com.zimasahealth.zcare.domains.reference.dto.MemberView;
import com.zimasahealth.zcare.domains.reference.entities.MemberReference;
import com.zimasahealth.zcare.domains.reference.mappers.MemberReferenceMapper;
import com.zimasahealth.zcare.domains.reference.repositories.MemberReferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Member references: found or created by source identity, one row per individual per source. */
@Service
@Transactional
public class MemberReferenceService {

    private final MemberReferenceRepository members;
    private final MemberReferenceMapper mapper;

    public MemberReferenceService(MemberReferenceRepository members, MemberReferenceMapper mapper) {
        this.members = members;
        this.mapper = mapper;
    }

    /** The reference for this identity, created on first sight and refreshed by newer source data. */
    public MemberView resolveOrCreate(MemberIdentity identity) {
        LocalDate sourceDate = identity.sourceDate() == null ? LocalDate.now() : identity.sourceDate();
        MemberReference member = members.findBySourceSystemAndSourceMemberNumberAndSourceIndividualRef(
                        identity.sourceSystem(), identity.sourceMemberNumber(), identity.sourceIndividualRef())
                .map(existing -> {
                    existing.refresh(identity.displayName(), identity.contactMsisdn(), sourceDate);
                    return existing;
                })
                .orElseGet(() -> members.save(new MemberReference(identity.sourceSystem(),
                        identity.sourceMemberNumber(), identity.sourceIndividualRef(), identity.displayName(),
                        identity.contactMsisdn(), sourceDate)));
        return mapper.toView(member);
    }

    @Transactional(readOnly = true)
    public Optional<MemberView> find(long memberId) {
        return members.findById(memberId).map(mapper::toView);
    }

    @Transactional(readOnly = true)
    public MemberView require(long memberId, String field) {
        return find(memberId).orElseThrow(() -> BusinessException.unknown(field, "member"));
    }
}

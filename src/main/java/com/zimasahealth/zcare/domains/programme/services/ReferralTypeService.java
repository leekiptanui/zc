package com.zimasahealth.zcare.domains.programme.services;

import java.util.Map;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.domains.programme.dto.CreateReferralTypeRequest;
import com.zimasahealth.zcare.domains.programme.dto.ReferralTypeView;
import com.zimasahealth.zcare.domains.programme.entities.ReferralType;
import com.zimasahealth.zcare.domains.programme.mappers.ProgrammeMapper;
import com.zimasahealth.zcare.domains.programme.repositories.ReferralTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Referral types: vocabulary held as tenant data, never as CHECK constraints (04D section 2.3). */
@Service
@Transactional
public class ReferralTypeService {

    private final ReferralTypeRepository referralTypes;
    private final ProgrammeMapper mapper;
    private final AuditWriter audit;

    public ReferralTypeService(ReferralTypeRepository referralTypes, ProgrammeMapper mapper, AuditWriter audit) {
        this.referralTypes = referralTypes;
        this.mapper = mapper;
        this.audit = audit;
    }

    public ServiceResult<ReferralTypeView> create(CreateReferralTypeRequest request) {
        if (referralTypes.findByCode(request.code()).isPresent()) {
            throw BusinessException.invalid("code", "Referral type '" + request.code() + "' already exists");
        }
        ReferralType type = referralTypes.save(new ReferralType(request.code(), request.name()));
        audit.record(AuditEntry.of(ProgrammeOperations.CREATE_REFERRAL_TYPE, "referral_type", type.getId())
                .newState(Map.of("code", type.getCode())));
        return ServiceResult.of(mapper.toView(type)).next("proceed").session("referralTypeId", type.getId());
    }
}

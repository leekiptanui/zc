package com.zimasahealth.zcare.domains.access.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.domains.access.dto.CreateOrganisationRequest;
import com.zimasahealth.zcare.domains.access.dto.OrganisationView;
import com.zimasahealth.zcare.domains.access.entities.Organisation;
import com.zimasahealth.zcare.domains.access.mappers.AccessMapper;
import com.zimasahealth.zcare.domains.access.repositories.OrganisationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Organisations within the tenant (ZCR-ADM-001): code unique per tenant. */
@Service
public class OrganisationService {

    private final OrganisationRepository organisations;
    private final AccessMapper mapper;
    private final AuditWriter audit;

    public OrganisationService(OrganisationRepository organisations, AccessMapper mapper, AuditWriter audit) {
        this.organisations = organisations;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OrganisationView> list() {
        return organisations.findAll(Sort.by("name")).stream().map(mapper::toView).toList();
    }

    @Transactional(readOnly = true)
    public Optional<OrganisationView> find(long id) {
        return organisations.findById(id).map(mapper::toView);
    }

    /** The organisation, which must exist in this tenant and be active. */
    @Transactional(readOnly = true)
    public OrganisationView requireActive(long id, String field) {
        OrganisationView organisation = find(id).orElseThrow(() -> BusinessException.unknown(field, "organisation"));
        if (!"active".equals(organisation.status())) {
            throw BusinessException.invalid(field, "Organisation " + id + " is " + organisation.status());
        }
        return organisation;
    }

    @Transactional
    public ServiceResult<OrganisationView> create(CreateOrganisationRequest request) {
        if (organisations.existsByCode(request.code())) {
            throw BusinessException.invalid("code", "Organisation code '" + request.code() + "' is already in use");
        }
        Organisation organisation = organisations.save(
                new Organisation(request.code(), request.name(), request.orgType()));
        audit.record(AuditEntry.of(AccessOperations.CREATE_ORGANISATION, "organisation", organisation.getId())
                .organisation(organisation.getId())
                .newState(Map.of("code", organisation.getCode(), "orgType", organisation.getOrgType())));
        return ServiceResult.of(mapper.toView(organisation)).next("proceed")
                .session("organisationId", organisation.getId());
    }
}

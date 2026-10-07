package com.zimasahealth.zcare.domains.access.controllers;

import java.util.List;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.common.api.CollectionResponse;
import com.zimasahealth.zcare.domains.access.dto.ConfigEntryView;
import com.zimasahealth.zcare.domains.access.dto.CreateOrganisationRequest;
import com.zimasahealth.zcare.domains.access.dto.OrganisationView;
import com.zimasahealth.zcare.domains.access.dto.SetConfigRequest;
import com.zimasahealth.zcare.domains.access.services.AccessOperations;
import com.zimasahealth.zcare.domains.access.services.ConfigService;
import com.zimasahealth.zcare.domains.access.services.OrganisationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Organisations and tenant configuration (M03 subset; OD-16: to be added to 04B). */
@RestController
@RequestMapping(ApiPaths.BASE)
public class AccessController {

    private final OrganisationService organisations;
    private final ConfigService config;

    public AccessController(OrganisationService organisations, ConfigService config) {
        this.organisations = organisations;
        this.config = config;
    }

    @GetMapping("/organisations")
    @PreAuthorize("hasAnyRole('PROGRAMME_ADMIN','PLATFORM_ADMIN')")
    @AuditOperation(AccessOperations.VIEW_ORGANISATIONS)
    public ApiResponse<CollectionResponse<OrganisationView>> listOrganisations() {
        return ApiResponse.success(CollectionResponse.of(organisations.list())).nextActions("proceed");
    }

    @PostMapping("/organisations")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditOperation(AccessOperations.CREATE_ORGANISATION)
    public ApiResponse<OrganisationView> createOrganisation(@Valid @RequestBody CreateOrganisationRequest request) {
        return ApiResponse.from(organisations.create(request));
    }

    @GetMapping("/config")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditOperation(AccessOperations.VIEW_CONFIG)
    public ApiResponse<CollectionResponse<ConfigEntryView>> listConfig() {
        List<ConfigEntryView> entries = config.inForce();
        return ApiResponse.success(CollectionResponse.of(entries)).nextActions("proceed");
    }

    @PostMapping("/config")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditOperation(AccessOperations.SET_CONFIG)
    public ApiResponse<ConfigEntryView> setConfig(@Valid @RequestBody SetConfigRequest request) {
        return ApiResponse.from(config.set(request));
    }
}

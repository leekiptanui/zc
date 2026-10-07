package com.zimasahealth.zcare.domains.access.mappers;

import com.zimasahealth.zcare.domains.access.dto.ConfigEntryView;
import com.zimasahealth.zcare.domains.access.dto.OrganisationView;
import com.zimasahealth.zcare.domains.access.entities.ConfigHistoryEntry;
import com.zimasahealth.zcare.domains.access.entities.Organisation;
import org.mapstruct.Mapper;

@Mapper
public interface AccessMapper {

    OrganisationView toView(Organisation organisation);

    ConfigEntryView toView(ConfigHistoryEntry entry);
}

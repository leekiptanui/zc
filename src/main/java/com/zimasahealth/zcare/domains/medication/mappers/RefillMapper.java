package com.zimasahealth.zcare.domains.medication.mappers;

import com.zimasahealth.zcare.domains.medication.dto.RefillView;
import com.zimasahealth.zcare.domains.medication.entities.RefillRequest;
import org.mapstruct.Mapper;

@Mapper
public interface RefillMapper {

    RefillView toView(RefillRequest refill);
}

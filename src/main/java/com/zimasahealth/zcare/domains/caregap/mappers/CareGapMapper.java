package com.zimasahealth.zcare.domains.caregap.mappers;

import com.zimasahealth.zcare.domains.caregap.dto.CareGapView;
import com.zimasahealth.zcare.domains.caregap.entities.CareGap;
import org.mapstruct.Mapper;

@Mapper
public interface CareGapMapper {

    CareGapView toView(CareGap gap);
}

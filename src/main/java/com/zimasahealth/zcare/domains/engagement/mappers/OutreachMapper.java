package com.zimasahealth.zcare.domains.engagement.mappers;

import com.zimasahealth.zcare.domains.engagement.dto.OutreachView;
import com.zimasahealth.zcare.domains.engagement.entities.OutreachRequest;
import org.mapstruct.Mapper;

@Mapper
public interface OutreachMapper {

    OutreachView toView(OutreachRequest request);
}

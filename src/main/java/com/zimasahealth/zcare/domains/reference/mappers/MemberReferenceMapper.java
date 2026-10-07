package com.zimasahealth.zcare.domains.reference.mappers;

import com.zimasahealth.zcare.domains.reference.dto.MemberView;
import com.zimasahealth.zcare.domains.reference.entities.MemberReference;
import org.mapstruct.Mapper;

@Mapper
public interface MemberReferenceMapper {

    MemberView toView(MemberReference member);
}

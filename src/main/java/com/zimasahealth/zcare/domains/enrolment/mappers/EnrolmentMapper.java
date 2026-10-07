package com.zimasahealth.zcare.domains.enrolment.mappers;

import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.entities.Enrolment;
import org.mapstruct.Mapper;

@Mapper
public interface EnrolmentMapper {

    EnrolmentView toView(Enrolment enrolment);
}

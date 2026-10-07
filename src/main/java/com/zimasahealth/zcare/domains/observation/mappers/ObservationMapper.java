package com.zimasahealth.zcare.domains.observation.mappers;

import com.zimasahealth.zcare.domains.observation.dto.ObservationView;
import com.zimasahealth.zcare.domains.observation.entities.Observation;
import org.mapstruct.Mapper;

@Mapper
public interface ObservationMapper {

    ObservationView toView(Observation observation);
}

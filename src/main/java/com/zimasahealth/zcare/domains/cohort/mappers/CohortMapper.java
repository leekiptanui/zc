package com.zimasahealth.zcare.domains.cohort.mappers;

import com.zimasahealth.zcare.domains.cohort.dto.CohortMembershipView;
import com.zimasahealth.zcare.domains.cohort.dto.CohortRunView;
import com.zimasahealth.zcare.domains.cohort.dto.CohortView;
import com.zimasahealth.zcare.domains.cohort.entities.Cohort;
import com.zimasahealth.zcare.domains.cohort.entities.CohortMembership;
import com.zimasahealth.zcare.domains.cohort.entities.CohortRun;
import org.mapstruct.Mapper;

@Mapper
public interface CohortMapper {

    CohortView toView(Cohort cohort, long includedMembers);

    CohortMembershipView toView(CohortMembership membership, boolean absorbed);

    CohortRunView toView(CohortRun run, int newMembers);
}

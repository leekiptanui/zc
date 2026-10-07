package com.zimasahealth.zcare.domains.programme.mappers;

import com.zimasahealth.zcare.domains.programme.dto.AssessmentTemplateVersionView;
import com.zimasahealth.zcare.domains.programme.dto.GapRuleView;
import com.zimasahealth.zcare.domains.programme.dto.GoalTypeView;
import com.zimasahealth.zcare.domains.programme.dto.ObservationTypeView;
import com.zimasahealth.zcare.domains.programme.dto.OutcomeDefinitionView;
import com.zimasahealth.zcare.domains.programme.dto.ProgrammeVersionSummary;
import com.zimasahealth.zcare.domains.programme.dto.ProgrammeVersionView;
import com.zimasahealth.zcare.domains.programme.dto.ReferralTypeView;
import com.zimasahealth.zcare.domains.programme.dto.TaskTemplateView;
import com.zimasahealth.zcare.domains.programme.entities.AssessmentTemplateVersion;
import com.zimasahealth.zcare.domains.programme.entities.GapRule;
import com.zimasahealth.zcare.domains.programme.entities.GoalType;
import com.zimasahealth.zcare.domains.programme.entities.ObservationType;
import com.zimasahealth.zcare.domains.programme.entities.OutcomeDefinition;
import com.zimasahealth.zcare.domains.programme.entities.ProgrammeVersion;
import com.zimasahealth.zcare.domains.programme.entities.ReferralType;
import com.zimasahealth.zcare.domains.programme.entities.TaskTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface ProgrammeMapper {

    /** Content key holding the consent wording a member must accept. */
    String CONSENT_WORDING_KEY = "consentWordingVersion";

    @Mapping(target = "clinicallyApproved", expression = "java(version.getClinicalApprovedBy() != null)")
    ProgrammeVersionSummary toSummary(ProgrammeVersion version);

    @Mapping(target = "consentWordingVersion", expression = "java(consentWording(version))")
    ProgrammeVersionView toView(ProgrammeVersion version);

    ObservationTypeView toView(ObservationType type);

    GoalTypeView toView(GoalType type);

    GapRuleView toView(GapRule rule);

    TaskTemplateView toView(TaskTemplate template);

    OutcomeDefinitionView toView(OutcomeDefinition definition);

    ReferralTypeView toView(ReferralType type);

    AssessmentTemplateVersionView toView(AssessmentTemplateVersion version);

    default String consentWording(ProgrammeVersion version) {
        Object value = version.getContent().get(CONSENT_WORDING_KEY);
        return value == null ? null : value.toString();
    }
}

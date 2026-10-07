package com.zimasahealth.zcare.domains.referral.mappers;

import com.zimasahealth.zcare.domains.referral.dto.ProviderActionView;
import com.zimasahealth.zcare.domains.referral.dto.ProviderParticipationView;
import com.zimasahealth.zcare.domains.referral.dto.ReferralView;
import com.zimasahealth.zcare.domains.referral.entities.ProviderAction;
import com.zimasahealth.zcare.domains.referral.entities.ProviderParticipation;
import com.zimasahealth.zcare.domains.referral.entities.Referral;
import org.mapstruct.Mapper;

@Mapper
public interface ReferralMapper {

    ReferralView toView(Referral referral);

    ProviderParticipationView toView(ProviderParticipation participation);

    ProviderActionView toView(ProviderAction action);
}

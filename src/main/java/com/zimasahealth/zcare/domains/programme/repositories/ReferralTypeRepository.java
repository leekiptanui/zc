package com.zimasahealth.zcare.domains.programme.repositories;

import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.domains.programme.entities.ReferralType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralTypeRepository extends JpaRepository<ReferralType, Long> {

    Optional<ReferralType> findByCode(String code);

    List<ReferralType> findByActiveTrueOrderByName();
}

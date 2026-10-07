package com.zimasahealth.zcare.domains.access.repositories;

import com.zimasahealth.zcare.domains.access.entities.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationRepository extends JpaRepository<Organisation, Long> {

    boolean existsByCode(String code);
}

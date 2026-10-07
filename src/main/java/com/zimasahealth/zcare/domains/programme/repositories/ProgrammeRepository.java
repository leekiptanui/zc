package com.zimasahealth.zcare.domains.programme.repositories;

import com.zimasahealth.zcare.domains.programme.entities.Programme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProgrammeRepository extends JpaRepository<Programme, Long>, JpaSpecificationExecutor<Programme> {

    boolean existsByCode(String code);
}

package com.zimasahealth.zcare.domains.programme.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.domains.programme.entities.ProgrammeVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgrammeVersionRepository extends JpaRepository<ProgrammeVersion, Long> {

    List<ProgrammeVersion> findByProgrammeIdOrderByVersionNumberDesc(Long programmeId);

    Optional<ProgrammeVersion> findFirstByProgrammeIdOrderByVersionNumberDesc(Long programmeId);

    Optional<ProgrammeVersion> findFirstByProgrammeIdAndStatusOrderByVersionNumberDesc(Long programmeId, String status);

    List<ProgrammeVersion> findByProgrammeIdAndStatusIn(Long programmeId, Collection<String> statuses);

    List<ProgrammeVersion> findByProgrammeIdIn(Collection<Long> programmeIds);
}

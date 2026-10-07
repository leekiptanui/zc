package com.zimasahealth.zcare.domains.programme.services;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.zimasahealth.zcare.common.api.PageParams;
import com.zimasahealth.zcare.common.api.PagedResponse;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.domains.programme.dto.AssessmentTemplateVersionView;
import com.zimasahealth.zcare.domains.programme.dto.GapRuleView;
import com.zimasahealth.zcare.domains.programme.dto.GoalTypeView;
import com.zimasahealth.zcare.domains.programme.dto.ObservationThreshold;
import com.zimasahealth.zcare.domains.programme.dto.ObservationTypeView;
import com.zimasahealth.zcare.domains.programme.dto.OutcomeDefinitionView;
import com.zimasahealth.zcare.domains.programme.dto.ProgrammeDetail;
import com.zimasahealth.zcare.domains.programme.dto.ProgrammeSummary;
import com.zimasahealth.zcare.domains.programme.dto.ProgrammeVersionDetail;
import com.zimasahealth.zcare.domains.programme.dto.ProgrammeVersionView;
import com.zimasahealth.zcare.domains.programme.dto.ReferralTypeView;
import com.zimasahealth.zcare.domains.programme.entities.Programme;
import com.zimasahealth.zcare.domains.programme.entities.ProgrammeVersion;
import com.zimasahealth.zcare.domains.programme.mappers.ProgrammeMapper;
import com.zimasahealth.zcare.domains.programme.repositories.AssessmentTemplateRepository;
import com.zimasahealth.zcare.domains.programme.repositories.AssessmentTemplateVersionRepository;
import com.zimasahealth.zcare.domains.programme.repositories.GapRuleRepository;
import com.zimasahealth.zcare.domains.programme.repositories.GoalTypeRepository;
import com.zimasahealth.zcare.domains.programme.repositories.ObservationTypeRepository;
import com.zimasahealth.zcare.domains.programme.repositories.OutcomeDefinitionRepository;
import com.zimasahealth.zcare.domains.programme.repositories.ProgrammeRepository;
import com.zimasahealth.zcare.domains.programme.repositories.ProgrammeVersionRepository;
import com.zimasahealth.zcare.domains.programme.repositories.ReferralTypeRepository;
import com.zimasahealth.zcare.domains.programme.repositories.TaskTemplateRepository;
import com.zimasahealth.zcare.domains.programme.specifications.ProgrammeSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Programme configuration as read by this domain's endpoints and by other domains. Other domains
 * see only these views, never the entities.
 */
@Service
@Transactional(readOnly = true)
public class ProgrammeQueryService {

    /** Content key holding observation thresholds: {@code {code: {reviewAbove, reviewBelow}}}. */
    static final String THRESHOLDS_KEY = "observationThresholds";

    private final ProgrammeRepository programmes;
    private final ProgrammeVersionRepository versions;
    private final ObservationTypeRepository observationTypes;
    private final GoalTypeRepository goalTypes;
    private final GapRuleRepository gapRules;
    private final TaskTemplateRepository taskTemplates;
    private final OutcomeDefinitionRepository outcomeDefinitions;
    private final ReferralTypeRepository referralTypes;
    private final AssessmentTemplateRepository templates;
    private final AssessmentTemplateVersionRepository templateVersions;
    private final ProgrammeMapper mapper;

    public ProgrammeQueryService(ProgrammeRepository programmes, ProgrammeVersionRepository versions,
                                 ObservationTypeRepository observationTypes, GoalTypeRepository goalTypes,
                                 GapRuleRepository gapRules, TaskTemplateRepository taskTemplates,
                                 OutcomeDefinitionRepository outcomeDefinitions, ReferralTypeRepository referralTypes,
                                 AssessmentTemplateRepository templates,
                                 AssessmentTemplateVersionRepository templateVersions, ProgrammeMapper mapper) {
        this.programmes = programmes;
        this.versions = versions;
        this.observationTypes = observationTypes;
        this.goalTypes = goalTypes;
        this.gapRules = gapRules;
        this.taskTemplates = taskTemplates;
        this.outcomeDefinitions = outcomeDefinitions;
        this.referralTypes = referralTypes;
        this.templates = templates;
        this.templateVersions = templateVersions;
        this.mapper = mapper;
    }

    public PagedResponse<ProgrammeSummary> list(String status, PageParams params) {
        Page<Programme> page = programmes.findAll(ProgrammeSpecifications.hasStatus(status),
                params.toPageable(Sort.by(Sort.Direction.DESC, "createdAt")));
        return PagedResponse.of(page, params, programme -> new ProgrammeSummary(programme.getId(),
                programme.getCode(), programme.getName(), programme.getCareModel(), programme.getStatus(),
                versions.findFirstByProgrammeIdOrderByVersionNumberDesc(programme.getId())
                        .map(mapper::toSummary).orElse(null)));
    }

    public ProgrammeDetail detail(long programmeId) {
        Programme programme = programmes.findById(programmeId)
                .orElseThrow(() -> BusinessException.unknown("programmeId", "programme"));
        List<ProgrammeVersion> all = versions.findByProgrammeIdOrderByVersionNumberDesc(programmeId);
        ProgrammeVersionDetail latest = all.isEmpty() ? null : versionDetail(all.get(0));
        return new ProgrammeDetail(programme.getId(), programme.getCode(), programme.getName(),
                programme.getCareModel(), programme.getStatus(), programme.getDescription(),
                all.stream().map(mapper::toSummary).toList(), latest);
    }

    ProgrammeVersionDetail versionDetail(ProgrammeVersion version) {
        Long id = version.getId();
        return new ProgrammeVersionDetail(id, version.getVersionNumber(), version.getStatus(), version.getContent(),
                version.getClinicalApprovedBy(), version.getClinicalApprovedAt(), version.getPublishedAt(),
                observationTypes.findByProgrammeVersionIdOrderByCode(id).stream().map(mapper::toView).toList(),
                goalTypes.findByProgrammeVersionIdOrderByCode(id).stream().map(mapper::toView).toList(),
                gapRules.findByProgrammeVersionIdOrderByGapTypeAscRuleVersionDesc(id).stream()
                        .map(mapper::toView).toList(),
                taskTemplates.findByProgrammeVersionIdOrderByTaskType(id).stream().map(mapper::toView).toList(),
                outcomeDefinitions.findByProgrammeVersionIdOrderByMeasureCodeAscMeasureVersionDesc(id).stream()
                        .map(mapper::toView).toList());
    }

    /** The version, which must exist in this tenant; an unknown id is reported on {@code field}. */
    public ProgrammeVersionView requireVersion(long versionId, String field) {
        return versions.findById(versionId).map(mapper::toView)
                .orElseThrow(() -> BusinessException.unknown(field, "programme version"));
    }

    /** The version, which must also be published: only published versions take members. */
    public ProgrammeVersionView requirePublishedVersion(long versionId, String field) {
        ProgrammeVersionView version = requireVersion(versionId, field);
        if (!version.isPublished()) {
            throw BusinessException.invalid(field, "Programme version " + versionId + " is " + version.status()
                    + "; only a published version can be used").with("programmeVersionStatus", version.status());
        }
        return version;
    }

    public List<ProgrammeVersionView> versionsOf(long programmeId) {
        return versions.findByProgrammeIdOrderByVersionNumberDesc(programmeId).stream().map(mapper::toView).toList();
    }

    public boolean programmeExists(long programmeId) {
        return programmes.existsById(programmeId);
    }

    public Optional<ObservationTypeView> observationType(long versionId, String code) {
        return observationTypes.findByProgrammeVersionIdAndCode(versionId, code).map(mapper::toView);
    }

    public Optional<GoalTypeView> goalType(long versionId, String code) {
        return goalTypes.findByProgrammeVersionIdAndCode(versionId, code).map(mapper::toView);
    }

    /** The current rule for a gap type: its highest active rule version. */
    public Optional<GapRuleView> activeGapRule(long versionId, String gapType) {
        return gapRules.findFirstByProgrammeVersionIdAndGapTypeAndActiveTrueOrderByRuleVersionDesc(versionId, gapType)
                .map(mapper::toView);
    }

    /** The latest version of a measure defined for the programme version. */
    public Optional<OutcomeDefinitionView> outcomeDefinition(long versionId, String measureCode) {
        return outcomeDefinitions.findFirstByProgrammeVersionIdAndMeasureCodeOrderByMeasureVersionDesc(
                versionId, measureCode).map(mapper::toView);
    }

    /** Every measure defined across the given programme versions. */
    public List<OutcomeDefinitionView> outcomeDefinitions(Collection<Long> versionIds) {
        return versionIds.isEmpty() ? List.of()
                : outcomeDefinitions.findByProgrammeVersionIdIn(versionIds).stream().map(mapper::toView).toList();
    }

    public Optional<ReferralTypeView> activeReferralType(String code) {
        return referralTypes.findByCode(code).filter(type -> type.isActive()).map(mapper::toView);
    }

    public List<ReferralTypeView> referralTypes() {
        return referralTypes.findByActiveTrueOrderByName().stream().map(mapper::toView).toList();
    }

    /** The assignable version of a questionnaire; with no code, the most recently published one. */
    public Optional<AssessmentTemplateVersionView> publishedTemplate(String templateCode) {
        if (templateCode == null) {
            return templateVersions.findFirstByStatusOrderByPublishedAtDesc("published").map(mapper::toView);
        }
        return templates.findByCode(templateCode)
                .flatMap(t -> templateVersions.findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(t.getId(),
                        "published"))
                .map(mapper::toView);
    }

    public AssessmentTemplateVersionView templateVersion(long templateVersionId) {
        return templateVersions.findById(templateVersionId).map(mapper::toView)
                .orElseThrow(() -> BusinessException.unknown("templateVersionId", "assessment template version"));
    }

    /** The review bounds configured for an observation type in a version, if any. */
    public Optional<ObservationThreshold> threshold(long versionId, String observationTypeCode) {
        return versions.findById(versionId)
                .map(version -> version.getContent().get(THRESHOLDS_KEY))
                .filter(Map.class::isInstance)
                .map(all -> ((Map<?, ?>) all).get(observationTypeCode))
                .filter(Map.class::isInstance)
                .map(bounds -> new ObservationThreshold(decimal(((Map<?, ?>) bounds).get("reviewAbove")),
                        decimal(((Map<?, ?>) bounds).get("reviewBelow"))));
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && !text.isBlank()) {
            return new BigDecimal(text.trim());
        }
        return null;
    }
}

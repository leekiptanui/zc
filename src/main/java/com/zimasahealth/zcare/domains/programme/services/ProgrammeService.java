package com.zimasahealth.zcare.domains.programme.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.error.ZCareExceptionCode;
import com.zimasahealth.zcare.common.outbox.DataClassification;
import com.zimasahealth.zcare.common.outbox.DomainEventType;
import com.zimasahealth.zcare.common.outbox.OutboxWriter;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.programme.dto.AddGoalTypeRequest;
import com.zimasahealth.zcare.domains.programme.dto.AddObservationTypeRequest;
import com.zimasahealth.zcare.domains.programme.dto.CreateProgrammeRequest;
import com.zimasahealth.zcare.domains.programme.dto.DefineOutcomeMeasureRequest;
import com.zimasahealth.zcare.domains.programme.dto.GapRuleView;
import com.zimasahealth.zcare.domains.programme.dto.GoalTypeView;
import com.zimasahealth.zcare.domains.programme.dto.ObservationTypeView;
import com.zimasahealth.zcare.domains.programme.dto.OutcomeDefinitionView;
import com.zimasahealth.zcare.domains.programme.dto.ProgrammeVersionState;
import com.zimasahealth.zcare.domains.programme.dto.SetGapRuleRequest;
import com.zimasahealth.zcare.domains.programme.dto.SetTaskTemplateRequest;
import com.zimasahealth.zcare.domains.programme.dto.SetThresholdRequest;
import com.zimasahealth.zcare.domains.programme.dto.TaskTemplateView;
import com.zimasahealth.zcare.domains.programme.entities.GapRule;
import com.zimasahealth.zcare.domains.programme.entities.GoalType;
import com.zimasahealth.zcare.domains.programme.entities.ObservationType;
import com.zimasahealth.zcare.domains.programme.entities.OutcomeDefinition;
import com.zimasahealth.zcare.domains.programme.entities.Programme;
import com.zimasahealth.zcare.domains.programme.entities.ProgrammeVersion;
import com.zimasahealth.zcare.domains.programme.entities.TaskTemplate;
import com.zimasahealth.zcare.domains.programme.mappers.ProgrammeMapper;
import com.zimasahealth.zcare.domains.programme.repositories.GapRuleRepository;
import com.zimasahealth.zcare.domains.programme.repositories.GoalTypeRepository;
import com.zimasahealth.zcare.domains.programme.repositories.ObservationTypeRepository;
import com.zimasahealth.zcare.domains.programme.repositories.OutcomeDefinitionRepository;
import com.zimasahealth.zcare.domains.programme.repositories.ProgrammeRepository;
import com.zimasahealth.zcare.domains.programme.repositories.ProgrammeVersionRepository;
import com.zimasahealth.zcare.domains.programme.repositories.TaskTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Programme lifecycle (04A section 7.1; UC-ZC-009). A programme is authored as a draft version,
 * approved by a clinician, then published; publication without recorded clinical approval is
 * impossible (ZCARE_CLINICAL_APPROVAL_REQUIRED). A published version is frozen with all of its
 * configuration: changes go into a new draft version. A clinical change to a draft (observation
 * types, thresholds, gap rules) voids its approval.
 */
@Service
@Transactional
public class ProgrammeService {

    private static final String RETIRED = "retired";

    private final ProgrammeRepository programmes;
    private final ProgrammeVersionRepository versions;
    private final ObservationTypeRepository observationTypes;
    private final GoalTypeRepository goalTypes;
    private final GapRuleRepository gapRules;
    private final TaskTemplateRepository taskTemplates;
    private final OutcomeDefinitionRepository outcomeDefinitions;
    private final ProgrammeMapper mapper;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public ProgrammeService(ProgrammeRepository programmes, ProgrammeVersionRepository versions,
                            ObservationTypeRepository observationTypes, GoalTypeRepository goalTypes,
                            GapRuleRepository gapRules, TaskTemplateRepository taskTemplates,
                            OutcomeDefinitionRepository outcomeDefinitions, ProgrammeMapper mapper,
                            AuditWriter audit, OutboxWriter outbox) {
        this.programmes = programmes;
        this.versions = versions;
        this.observationTypes = observationTypes;
        this.goalTypes = goalTypes;
        this.gapRules = gapRules;
        this.taskTemplates = taskTemplates;
        this.outcomeDefinitions = outcomeDefinitions;
        this.mapper = mapper;
        this.audit = audit;
        this.outbox = outbox;
    }

    public ServiceResult<ProgrammeVersionState> create(CreateProgrammeRequest request) {
        if (programmes.existsByCode(request.code())) {
            throw BusinessException.invalid("code", "Programme code '" + request.code() + "' is already in use");
        }
        Programme programme = programmes.save(new Programme(request.code(), request.name(), request.careModel(),
                request.description()));
        Map<String, Object> content = new LinkedHashMap<>(request.content() == null ? Map.of() : request.content());
        content.put(ProgrammeMapper.CONSENT_WORDING_KEY, request.consentWordingVersion());
        ProgrammeVersion version = versions.save(new ProgrammeVersion(programme.getId(), 1, content));
        audit.record(AuditEntry.of(ProgrammeOperations.CREATE_PROGRAMME, "programme", programme.getId())
                .programmeVersion(version.getId())
                .newState(Map.of("code", programme.getCode(), "careModel", programme.getCareModel())));
        return ServiceResult.of(state(version))
                .next("add_observation_type", "add_goal_type", "record_clinical_approval")
                .session("programmeId", programme.getId())
                .session("programmeVersionId", version.getId());
    }

    /** A new draft that copies the latest version's content and configuration, but not its approval. */
    public ServiceResult<ProgrammeVersionState> createVersion(long programmeId) {
        Programme programme = requireLiveProgramme(programmeId);
        ProgrammeVersion latest = versions.findFirstByProgrammeIdOrderByVersionNumberDesc(programmeId)
                .orElseThrow(() -> BusinessException.unknown("programmeId", "programme"));
        if ("draft".equals(latest.getStatus())) {
            throw BusinessException.invalid("programmeId", "Version " + latest.getVersionNumber()
                    + " is still a draft; edit or publish it first").with("programmeVersionId", latest.getId());
        }
        ProgrammeVersion draft = versions.save(new ProgrammeVersion(programme.getId(),
                latest.getVersionNumber() + 1, latest.getContent()));
        Long from = latest.getId();
        Long to = draft.getId();
        observationTypes.findByProgrammeVersionIdOrderByCode(from).forEach(t -> observationTypes.save(t.copyTo(to)));
        goalTypes.findByProgrammeVersionIdOrderByCode(from).forEach(t -> goalTypes.save(t.copyTo(to)));
        gapRules.findByProgrammeVersionIdAndActiveTrue(from).forEach(r -> gapRules.save(r.copyTo(to)));
        taskTemplates.findByProgrammeVersionIdOrderByTaskType(from).forEach(t -> taskTemplates.save(t.copyTo(to)));
        outcomeDefinitions.findByProgrammeVersionIdOrderByMeasureCodeAscMeasureVersionDesc(from)
                .forEach(d -> outcomeDefinitions.save(d.copyTo(to)));
        audit.record(AuditEntry.of(ProgrammeOperations.CREATE_PROGRAMME_VERSION, "programme_version", to)
                .programmeVersion(to)
                .newState(Map.of("versionNumber", draft.getVersionNumber(), "copiedFromVersion",
                        latest.getVersionNumber())));
        return ServiceResult.of(state(draft)).next("record_clinical_approval")
                .session("programmeId", programmeId).session("programmeVersionId", to);
    }

    public ServiceResult<ObservationTypeView> addObservationType(long programmeId, AddObservationTypeRequest request) {
        ProgrammeVersion draft = requireDraft(programmeId);
        if (observationTypes.findByProgrammeVersionIdAndCode(draft.getId(), request.code()).isPresent()) {
            throw BusinessException.invalid("code", "Observation type '" + request.code()
                    + "' is already defined on this version");
        }
        if (request.plausibleMin() != null && request.plausibleMax() != null
                && request.plausibleMin().compareTo(request.plausibleMax()) > 0) {
            throw BusinessException.invalid("plausibleMin", "plausibleMin cannot exceed plausibleMax");
        }
        ObservationType type = observationTypes.save(new ObservationType(draft.getId(), request.code(),
                request.name(), request.unit(), request.loincCode(), request.plausibleMin(), request.plausibleMax()));
        draft.clearClinicalApproval();
        audit.record(AuditEntry.of(ProgrammeOperations.ADD_OBSERVATION_TYPE, "observation_type", type.getId())
                .programmeVersion(draft.getId())
                .newState(Map.of("code", type.getCode(), "unit", type.getUnit())));
        return ServiceResult.of(mapper.toView(type)).next("set_threshold", "record_clinical_approval")
                .session("programmeVersionId", draft.getId()).session("observationTypeId", type.getId());
    }

    public ServiceResult<ProgrammeVersionState> setThreshold(long programmeId, SetThresholdRequest request) {
        ProgrammeVersion draft = requireDraft(programmeId);
        if (observationTypes.findByProgrammeVersionIdAndCode(draft.getId(), request.observationTypeCode()).isEmpty()) {
            throw BusinessException.invalid("observationTypeCode", "Observation type '"
                    + request.observationTypeCode() + "' is not defined on this version");
        }
        if (request.reviewAbove() == null && request.reviewBelow() == null) {
            throw BusinessException.required("reviewAbove", "Give reviewAbove, reviewBelow or both");
        }
        if (request.reviewAbove() != null && request.reviewBelow() != null
                && request.reviewBelow().compareTo(request.reviewAbove()) > 0) {
            throw BusinessException.invalid("reviewBelow", "reviewBelow cannot exceed reviewAbove");
        }
        Map<String, Object> content = new LinkedHashMap<>(draft.getContent());
        Map<String, Object> thresholds = new LinkedHashMap<>();
        if (content.get(ProgrammeQueryService.THRESHOLDS_KEY) instanceof Map<?, ?> existing) {
            existing.forEach((k, v) -> thresholds.put(String.valueOf(k), v));
        }
        Map<String, Object> bounds = new LinkedHashMap<>();
        bounds.put("reviewAbove", plain(request.reviewAbove()));
        bounds.put("reviewBelow", plain(request.reviewBelow()));
        thresholds.put(request.observationTypeCode(), bounds);
        content.put(ProgrammeQueryService.THRESHOLDS_KEY, thresholds);
        draft.setContent(content);
        draft.clearClinicalApproval();
        audit.record(AuditEntry.of(ProgrammeOperations.SET_OBSERVATION_THRESHOLD, "programme_version", draft.getId())
                .programmeVersion(draft.getId())
                .newState(Map.of("observationTypeCode", request.observationTypeCode(), "bounds", bounds)));
        return ServiceResult.of(state(draft)).next("record_clinical_approval")
                .session("programmeVersionId", draft.getId());
    }

    public ServiceResult<GoalTypeView> addGoalType(long programmeId, AddGoalTypeRequest request) {
        ProgrammeVersion draft = requireDraft(programmeId);
        if (goalTypes.findByProgrammeVersionIdAndCode(draft.getId(), request.code()).isPresent()) {
            throw BusinessException.invalid("code", "Goal type '" + request.code() + "' is already defined");
        }
        GoalType type = goalTypes.save(new GoalType(draft.getId(), request.code(), request.name()));
        audit.record(AuditEntry.of(ProgrammeOperations.ADD_GOAL_TYPE, "goal_type", type.getId())
                .programmeVersion(draft.getId()).newState(Map.of("code", type.getCode())));
        return ServiceResult.of(mapper.toView(type)).next("proceed")
                .session("programmeVersionId", draft.getId()).session("goalTypeId", type.getId());
    }

    /** A changed rule is a new rule version; the previous one stays, inactive, for explainability. */
    public ServiceResult<GapRuleView> setGapRule(long programmeId, SetGapRuleRequest request) {
        ProgrammeVersion draft = requireDraft(programmeId);
        int ruleVersion = gapRules.findFirstByProgrammeVersionIdAndGapTypeOrderByRuleVersionDesc(draft.getId(),
                request.gapType()).map(previous -> {
                    previous.deactivate();
                    return previous.getRuleVersion() + 1;
                }).orElse(1);
        GapRule rule = gapRules.save(new GapRule(draft.getId(), request.gapType(), ruleVersion,
                request.periodStrategy(), request.definition(), request.description()));
        draft.clearClinicalApproval();
        audit.record(AuditEntry.of(ProgrammeOperations.SET_GAP_RULE, "gap_rule", rule.getId())
                .programmeVersion(draft.getId())
                .newState(Map.of("gapType", rule.getGapType(), "ruleVersion", ruleVersion)));
        return ServiceResult.of(mapper.toView(rule)).next("record_clinical_approval")
                .session("programmeVersionId", draft.getId()).session("gapRuleId", rule.getId());
    }

    public ServiceResult<TaskTemplateView> setTaskTemplate(long programmeId, SetTaskTemplateRequest request) {
        ProgrammeVersion draft = requireDraft(programmeId);
        TaskTemplate template = taskTemplates.findByProgrammeVersionIdAndTaskType(draft.getId(), request.taskType())
                .orElseGet(() -> new TaskTemplate(draft.getId(), request.taskType()));
        template.update(request.name(), request.description(),
                (short) (request.defaultPriority() == null ? 3 : request.defaultPriority()),
                request.slaHours(), request.defaultAssigneeRole());
        template = taskTemplates.save(template);
        audit.record(AuditEntry.of(ProgrammeOperations.SET_TASK_TEMPLATE, "task_template", template.getId())
                .programmeVersion(draft.getId()).newState(Map.of("taskType", template.getTaskType())));
        return ServiceResult.of(mapper.toView(template)).next("proceed")
                .session("programmeVersionId", draft.getId()).session("taskTemplateId", template.getId());
    }

    /** A redefined measure is a new measure version; reported figures always name theirs. */
    public ServiceResult<OutcomeDefinitionView> defineOutcomeMeasure(long programmeId,
                                                                     DefineOutcomeMeasureRequest request) {
        ProgrammeVersion draft = requireDraft(programmeId);
        int measureVersion = outcomeDefinitions.findFirstByProgrammeVersionIdAndMeasureCodeOrderByMeasureVersionDesc(
                draft.getId(), request.measureCode()).map(d -> d.getMeasureVersion() + 1).orElse(1);
        OutcomeDefinition definition = outcomeDefinitions.save(new OutcomeDefinition(draft.getId(),
                request.measureCode(), measureVersion, request.name(), request.category(), request.definition()));
        audit.record(AuditEntry.of(ProgrammeOperations.DEFINE_OUTCOME_MEASURE, "outcome_definition",
                        definition.getId())
                .programmeVersion(draft.getId())
                .newState(Map.of("measureCode", definition.getMeasureCode(), "measureVersion", measureVersion)));
        return ServiceResult.of(mapper.toView(definition)).next("proceed")
                .session("programmeVersionId", draft.getId()).session("outcomeDefinitionId", definition.getId());
    }

    /** The clinician's approval of the current draft (US-CLN-06). Approving twice is absorbed. */
    public ServiceResult<ProgrammeVersionState> recordClinicalApproval(long programmeId) {
        ProgrammeVersion draft = requireDraft(programmeId);
        if (draft.getClinicalApprovedBy() == null) {
            draft.recordClinicalApproval(CurrentActor.require().id(), Times.now());
            audit.record(AuditEntry.of(ProgrammeOperations.RECORD_CLINICAL_APPROVAL, "programme_version",
                            draft.getId())
                    .programmeVersion(draft.getId())
                    .newState(Map.of("clinicalApprovedBy", draft.getClinicalApprovedBy())));
        }
        return ServiceResult.of(state(draft)).next("publish_programme")
                .session("programmeId", programmeId).session("programmeVersionId", draft.getId());
    }

    public ServiceResult<ProgrammeVersionState> publish(long programmeId) {
        Programme programme = requireLiveProgramme(programmeId);
        ProgrammeVersion draft = versions.findFirstByProgrammeIdAndStatusOrderByVersionNumberDesc(programmeId, "draft")
                .orElseThrow(() -> BusinessException.invalid("programmeId",
                        "There is no draft version to publish; create one with POST /programmes/{id}/versions"));
        if (draft.getClinicalApprovedBy() == null) {
            throw BusinessException.of(ZCareExceptionCode.ZCARE_CLINICAL_APPROVAL_REQUIRED,
                            "Publication is impossible until a clinician records approval of version "
                                    + draft.getVersionNumber())
                    .with("programmeVersionId", draft.getId())
                    .data(state(draft));
        }
        Instant now = Times.now();
        draft.setStatus("published");
        draft.setPublishedAt(now);
        if (!"active".equals(programme.getStatus())) {
            programme.setStatus("active");
        }
        outbox.emit(DomainEventType.ProgrammePublished, "programme_version", draft.getId(),
                Map.of("programmeId", programmeId, "programmeVersionId", draft.getId(),
                        "versionNumber", draft.getVersionNumber()),
                DataClassification.NON_PHI);
        audit.record(AuditEntry.of(ProgrammeOperations.PUBLISH_PROGRAMME, "programme_version", draft.getId())
                .programmeVersion(draft.getId())
                .newState(Map.of("status", "published", "clinicalApprovedBy", draft.getClinicalApprovedBy())));
        return ServiceResult.of(state(draft)).next("create_cohort")
                .session("programmeId", programmeId).session("programmeVersionId", draft.getId());
    }

    /**
     * Retirement stops new members joining; existing enrolments stay pinned to their version and
     * are untouched. Published and open versions are retired with it.
     */
    public ServiceResult<ProgrammeVersionState> retire(long programmeId) {
        Programme programme = requireLiveProgramme(programmeId);
        Instant now = Times.now();
        List<ProgrammeVersion> open = versions.findByProgrammeIdAndStatusIn(programmeId,
                List.of("draft", "pending_approval", "published"));
        open.forEach(version -> {
            version.setStatus(RETIRED);
            version.setRetiredAt(now);
        });
        programme.setStatus(RETIRED);
        outbox.emit(DomainEventType.ProgrammeRetired, "programme", programmeId,
                Map.of("programmeId", programmeId), DataClassification.NON_PHI);
        audit.record(AuditEntry.of(ProgrammeOperations.RETIRE_PROGRAMME, "programme", programmeId)
                .newState(Map.of("status", RETIRED, "versionsRetired", open.size())));
        ProgrammeVersion latest = versions.findFirstByProgrammeIdOrderByVersionNumberDesc(programmeId).orElseThrow();
        return ServiceResult.of(new ProgrammeVersionState(programmeId, latest.getId(), latest.getVersionNumber(),
                RETIRED, latest.getClinicalApprovedBy() != null)).next("proceed").session("programmeId", programmeId);
    }

    private Programme requireLiveProgramme(long programmeId) {
        Programme programme = programmes.findById(programmeId)
                .orElseThrow(() -> BusinessException.unknown("programmeId", "programme"));
        if (RETIRED.equals(programme.getStatus())) {
            throw BusinessException.invalid("programmeId", "Programme " + programmeId + " is retired");
        }
        return programme;
    }

    /** The programme's draft version; configuration of any other version is frozen. */
    private ProgrammeVersion requireDraft(long programmeId) {
        requireLiveProgramme(programmeId);
        ProgrammeVersion latest = versions.findFirstByProgrammeIdOrderByVersionNumberDesc(programmeId)
                .orElseThrow(() -> BusinessException.unknown("programmeId", "programme"));
        if (!"draft".equals(latest.getStatus())) {
            throw BusinessException.of(ZCareExceptionCode.ZCARE_PROGRAMME_VERSION_IMMUTABLE,
                            "Version " + latest.getVersionNumber() + " is " + latest.getStatus()
                                    + " and cannot change; create a new version to make changes")
                    .with("programmeVersionId", latest.getId());
        }
        return latest;
    }

    private static ProgrammeVersionState state(ProgrammeVersion version) {
        return new ProgrammeVersionState(version.getProgrammeId(), version.getId(), version.getVersionNumber(),
                version.getStatus(), version.getClinicalApprovedBy() != null);
    }

    private static Object plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}

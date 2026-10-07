package com.zimasahealth.zcare.domains.programme.services;

import java.util.List;
import java.util.Map;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.programme.dto.AssessmentTemplateSummary;
import com.zimasahealth.zcare.domains.programme.dto.AssessmentTemplateVersionView;
import com.zimasahealth.zcare.domains.programme.dto.CreateAssessmentTemplateRequest;
import com.zimasahealth.zcare.domains.programme.entities.AssessmentTemplate;
import com.zimasahealth.zcare.domains.programme.entities.AssessmentTemplateVersion;
import com.zimasahealth.zcare.domains.programme.mappers.ProgrammeMapper;
import com.zimasahealth.zcare.domains.programme.repositories.AssessmentTemplateRepository;
import com.zimasahealth.zcare.domains.programme.repositories.AssessmentTemplateVersionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Questionnaires (US-PA-04). A template is authored as a draft and published; publishing a new
 * version retires the previous one, so one version per template is assignable at a time.
 *
 * <p>The agreed schema has no clinical-approval columns on template versions, so the
 * clinician sign-off the trial system required before publication cannot be recorded yet;
 * it needs a schema change (raised with OD-17).
 */
@Service
@Transactional
public class AssessmentTemplateService {

    private final AssessmentTemplateRepository templates;
    private final AssessmentTemplateVersionRepository versions;
    private final ProgrammeMapper mapper;
    private final AuditWriter audit;

    public AssessmentTemplateService(AssessmentTemplateRepository templates,
                                     AssessmentTemplateVersionRepository versions, ProgrammeMapper mapper,
                                     AuditWriter audit) {
        this.templates = templates;
        this.versions = versions;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AssessmentTemplateSummary> list() {
        return templates.findAll(Sort.by("name")).stream().map(template -> {
            List<AssessmentTemplateVersion> all = versions.findByTemplateIdOrderByVersionNumberDesc(template.getId());
            AssessmentTemplateVersion latest = all.isEmpty() ? null : all.get(0);
            Long published = all.stream().filter(v -> "published".equals(v.getStatus()))
                    .map(AssessmentTemplateVersion::getId).findFirst().orElse(null);
            return new AssessmentTemplateSummary(template.getId(), template.getCode(), template.getName(),
                    latest == null ? null : latest.getVersionNumber(), latest == null ? null : latest.getStatus(),
                    published);
        }).toList();
    }

    public ServiceResult<AssessmentTemplateVersionView> create(CreateAssessmentTemplateRequest request) {
        if (templates.existsByCode(request.code())) {
            throw BusinessException.invalid("code", "Assessment template '" + request.code() + "' already exists");
        }
        requireQuestions(request.questionnaire());
        AssessmentTemplate template = templates.save(new AssessmentTemplate(request.code(), request.name(),
                request.description()));
        AssessmentTemplateVersion version = versions.save(new AssessmentTemplateVersion(template.getId(), 1,
                request.questionnaire(), request.scoringRules()));
        audit.record(AuditEntry.of(ProgrammeOperations.CREATE_ASSESSMENT_TEMPLATE, "assessment_template",
                template.getId()).newState(Map.of("code", template.getCode(), "versionNumber", 1)));
        return ServiceResult.of(mapper.toView(version)).next("publish_assessment_template")
                .session("assessmentTemplateId", template.getId())
                .session("assessmentTemplateVersionId", version.getId());
    }

    public ServiceResult<AssessmentTemplateVersionView> publish(long templateId) {
        if (!templates.existsById(templateId)) {
            throw BusinessException.unknown("templateId", "assessment template");
        }
        AssessmentTemplateVersion draft = versions.findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(templateId,
                "draft").orElseThrow(() -> BusinessException.invalid("templateId",
                "There is no draft version of this template to publish"));
        versions.findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(templateId, "published")
                .ifPresent(previous -> previous.retire(Times.now()));
        draft.publish(Times.now());
        audit.record(AuditEntry.of(ProgrammeOperations.PUBLISH_ASSESSMENT_TEMPLATE, "assessment_template_version",
                draft.getId()).newState(Map.of("status", "published", "versionNumber", draft.getVersionNumber())));
        return ServiceResult.of(mapper.toView(draft)).next("proceed")
                .session("assessmentTemplateId", templateId)
                .session("assessmentTemplateVersionId", draft.getId());
    }

    private static void requireQuestions(Map<String, Object> questionnaire) {
        if (!(questionnaire.get("questions") instanceof List<?> questions) || questions.isEmpty()) {
            throw BusinessException.invalid("questionnaire.questions", "A questionnaire needs at least one question");
        }
        for (int i = 0; i < questions.size(); i++) {
            if (!(questions.get(i) instanceof Map<?, ?> question)
                    || !(question.get("code") instanceof String code) || code.isBlank()) {
                throw BusinessException.invalid("questionnaire.questions[" + i + "].code", "Every question needs a code");
            }
        }
    }
}

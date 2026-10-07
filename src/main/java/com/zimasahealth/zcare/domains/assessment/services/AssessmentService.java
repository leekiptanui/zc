package com.zimasahealth.zcare.domains.assessment.services;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.consent.ConsentGate;
import com.zimasahealth.zcare.common.consent.ContentClass;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.outbox.DataClassification;
import com.zimasahealth.zcare.common.outbox.DomainEventType;
import com.zimasahealth.zcare.common.outbox.OutboxWriter;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.assessment.dto.AssessmentResult;
import com.zimasahealth.zcare.domains.assessment.dto.AssessmentView;
import com.zimasahealth.zcare.domains.assessment.dto.AssignAssessmentRequest;
import com.zimasahealth.zcare.domains.assessment.dto.CompleteAssessmentRequest;
import com.zimasahealth.zcare.domains.assessment.entities.Assessment;
import com.zimasahealth.zcare.domains.assessment.entities.AssessmentResponse;
import com.zimasahealth.zcare.domains.assessment.entities.RiskClassification;
import com.zimasahealth.zcare.domains.assessment.repositories.AssessmentRepository;
import com.zimasahealth.zcare.domains.assessment.repositories.AssessmentResponseRepository;
import com.zimasahealth.zcare.domains.assessment.repositories.RiskClassificationRepository;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.services.EnrolmentQueryService;
import com.zimasahealth.zcare.domains.programme.dto.AssessmentTemplateVersionView;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assessment and priority classification (04A section 7.4; UC-ZC-012). An incomplete assessment
 * is never scored as complete; responses and classifications are appended, never overwritten.
 */
@Service
@Transactional
public class AssessmentService {

    private static final List<String> OPEN = List.of("assigned", "in_progress");

    private final AssessmentRepository assessments;
    private final AssessmentResponseRepository responses;
    private final RiskClassificationRepository classifications;
    private final EnrolmentQueryService enrolments;
    private final ProgrammeQueryService programmes;
    private final ConsentGate consentGate;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public AssessmentService(AssessmentRepository assessments, AssessmentResponseRepository responses,
                             RiskClassificationRepository classifications, EnrolmentQueryService enrolments,
                             ProgrammeQueryService programmes, ConsentGate consentGate, AuditWriter audit,
                             OutboxWriter outbox) {
        this.assessments = assessments;
        this.responses = responses;
        this.classifications = classifications;
        this.enrolments = enrolments;
        this.programmes = programmes;
        this.consentGate = consentGate;
        this.audit = audit;
        this.outbox = outbox;
    }

    /** Assigns the published questionnaire, pinning its version; an open one is resumed, not stacked. */
    public ServiceResult<AssessmentView> assign(long enrolmentId, AssignAssessmentRequest request) {
        EnrolmentView enrolment = enrolments.requireActive(enrolmentId, "enrolmentId");
        consentGate.require(enrolmentId, ContentClass.HEALTH_CONTENT);
        String templateCode = request == null ? null : request.templateCode();
        AssessmentTemplateVersionView template = programmes.publishedTemplate(templateCode)
                .orElseThrow(() -> BusinessException.invalid("templateCode", templateCode == null
                        ? "No published assessment template is available"
                        : "Assessment template '" + templateCode + "' has no published version"));
        Optional<Assessment> open = assessments.findFirstByEnrolmentIdAndTemplateVersionIdAndStatusIn(enrolmentId,
                template.id(), OPEN);
        Assessment assessment = open.orElseGet(() -> assessments.save(new Assessment(enrolmentId, template.id(),
                Times.now(), request == null ? null : request.dueAt())));
        if (open.isEmpty()) {
            audit.record(AuditEntry.of(AssessmentOperations.ASSIGN_ASSESSMENT, "assessment", assessment.getId())
                    .member(enrolment.memberId()).programmeVersion(enrolment.programmeVersionId())
                    .newState(Map.of("templateVersionId", template.id())));
        }
        return ServiceResult.of(view(assessment, template, open.isPresent())).next("complete_assessment")
                .session("enrolmentId", enrolmentId).session("assessmentId", assessment.getId());
    }

    public ServiceResult<AssessmentResult> complete(long assessmentId, CompleteAssessmentRequest request) {
        Assessment assessment = assessments.findById(assessmentId)
                .orElseThrow(() -> BusinessException.unknown("assessmentId", "assessment"));
        if (!assessment.isOpen()) {
            throw BusinessException.invalid("assessmentId", "The assessment is " + assessment.getStatus());
        }
        EnrolmentView enrolment = enrolments.require(assessment.getEnrolmentId(), "assessmentId");
        consentGate.require(enrolment.id(), ContentClass.HEALTH_CONTENT);
        AssessmentTemplateVersionView template = programmes.templateVersion(assessment.getTemplateVersionId());

        List<String> missing = requiredQuestions(template).stream()
                .filter(code -> request.answers().get(code) == null || request.answers().get(code).isBlank())
                .toList();
        if (!missing.isEmpty()) {
            throw BusinessException.invalid("answers." + missing.get(0),
                            "The assessment is incomplete; required questions are unanswered: " + missing)
                    .with("missingQuestions", missing);
        }

        Instant now = Times.now();
        request.answers().forEach((question, answer) ->
                responses.save(new AssessmentResponse(assessmentId, question, answer, now)));
        AssessmentScorer.Score score = AssessmentScorer.score(
                template.scoringRules() == null ? Map.of() : template.scoringRules(), request.answers());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("points", score.perQuestion());
        detail.put("tier", score.tier());
        detail.put("templateVersion", template.versionNumber());
        assessment.complete(score.total(), detail, now);

        Long classificationId = null;
        if (score.tier() != null) {
            RiskClassification classification = classifications.save(RiskClassification.fromAssessment(
                    enrolment.id(), assessmentId, score.tier(),
                    "Score " + score.total().toPlainString() + " on template version " + template.versionNumber(),
                    now));
            classificationId = classification.getId();
            outbox.emit(DomainEventType.RiskClassificationChanged, "enrolment", enrolment.id(),
                    Map.of("enrolmentId", enrolment.id(), "assessmentId", assessmentId,
                            "priorityTier", score.tier()),
                    DataClassification.PHI);
        }
        outbox.emit(DomainEventType.AssessmentCompleted, "assessment", assessmentId,
                Map.of("assessmentId", assessmentId, "enrolmentId", enrolment.id(), "score", score.total()),
                DataClassification.PHI);
        audit.record(AuditEntry.of(AssessmentOperations.COMPLETE_ASSESSMENT, "assessment", assessmentId)
                .member(enrolment.memberId()).programmeVersion(enrolment.programmeVersionId())
                .newState(Map.of("status", "completed", "score", score.total())));
        return ServiceResult.of(new AssessmentResult(assessmentId, enrolment.id(), assessment.getStatus(),
                        score.total(), score.tier(), classificationId,
                        "Programme priority, not a diagnosis (ZCR-ASM-004)"))
                .next("view_member_context")
                .session("assessmentId", assessmentId).session("enrolmentId", enrolment.id())
                .session("riskClassificationId", classificationId);
    }

    private static List<String> requiredQuestions(AssessmentTemplateVersionView template) {
        if (!(template.questionnaire().get("questions") instanceof List<?> questions)) {
            return List.of();
        }
        return questions.stream()
                .filter(q -> q instanceof Map<?, ?> question && !Boolean.FALSE.equals(question.get("required")))
                .map(q -> String.valueOf(((Map<?, ?>) q).get("code")))
                .toList();
    }

    private static AssessmentView view(Assessment assessment, AssessmentTemplateVersionView template, boolean resumed) {
        Object questions = template.questionnaire().get("questions");
        return new AssessmentView(assessment.getId(), assessment.getEnrolmentId(), assessment.getTemplateVersionId(),
                assessment.getStatus(), assessment.getAssignedAt(), assessment.getDueAt(),
                questions instanceof List<?> list ? List.copyOf(list) : List.of(), resumed);
    }
}

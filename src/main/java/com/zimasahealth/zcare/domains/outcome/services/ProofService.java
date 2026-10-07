package com.zimasahealth.zcare.domains.outcome.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.context.Actor;
import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.common.context.ZcareRole;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.error.ZCareExceptionCode;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.access.services.ConfigKeys;
import com.zimasahealth.zcare.domains.access.services.ConfigService;
import com.zimasahealth.zcare.domains.caregap.services.CareGapService;
import com.zimasahealth.zcare.domains.careplan.services.CarePlanQueryService;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentCounts;
import com.zimasahealth.zcare.domains.enrolment.services.EnrolmentQueryService;
import com.zimasahealth.zcare.domains.outcome.dto.ProgrammeProof;
import com.zimasahealth.zcare.domains.outcome.entities.ReportSnapshot;
import com.zimasahealth.zcare.domains.outcome.repositories.OutcomeObservationRepository;
import com.zimasahealth.zcare.domains.outcome.repositories.ReportSnapshotRepository;
import com.zimasahealth.zcare.domains.programme.dto.OutcomeDefinitionView;
import com.zimasahealth.zcare.domains.programme.dto.ProgrammeVersionView;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeQueryService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregate programme proof (UC-ZC-008; ADR-008, ADR-009). Audience rules are absolute: employer
 * figures from a population below the minimum are withheld entirely and named; a measure with
 * fewer contributors than the minimum is withheld too, so no cell can re-identify anyone. The
 * minimum is the DEC-014 floor of 10, which configuration may only raise. Every report issued is
 * frozen as a snapshot.
 */
@Service
@Transactional
public class ProofService {

    static final int POPULATION_FLOOR = 10;
    static final String EMPLOYER = "employer_aggregate";
    private static final Map<ZcareRole, Set<String>> AUDIENCES = Map.of(
            ZcareRole.PROGRAMME_ADMIN, Set.of("internal", "payer", EMPLOYER),
            ZcareRole.PAYER_MANAGER, Set.of("payer"),
            ZcareRole.EMPLOYER_SPONSOR, Set.of(EMPLOYER));
    private static final String ATTRIBUTION = "Correlation only: no attribution methodology is agreed, and no"
            + " savings are claimed";

    private final ProgrammeQueryService programmes;
    private final EnrolmentQueryService enrolments;
    private final CarePlanQueryService carePlans;
    private final CareGapService careGaps;
    private final OutcomeObservationRepository outcomes;
    private final ReportSnapshotRepository snapshots;
    private final ConfigService config;
    private final AuditWriter audit;

    public ProofService(ProgrammeQueryService programmes, EnrolmentQueryService enrolments,
                        CarePlanQueryService carePlans, CareGapService careGaps,
                        OutcomeObservationRepository outcomes, ReportSnapshotRepository snapshots,
                        ConfigService config, AuditWriter audit) {
        this.programmes = programmes;
        this.enrolments = enrolments;
        this.carePlans = carePlans;
        this.careGaps = careGaps;
        this.outcomes = outcomes;
        this.snapshots = snapshots;
        this.config = config;
        this.audit = audit;
    }

    public ServiceResult<ProgrammeProof> proof(long programmeId, String audience) {
        Actor actor = CurrentActor.require();
        boolean permitted = actor.roles().stream()
                .anyMatch(role -> AUDIENCES.getOrDefault(role, Set.of()).contains(audience));
        if (!permitted) {
            throw new AccessDeniedException("Audience '" + audience + "' is not permitted for the caller's role");
        }
        List<ProgrammeVersionView> versions = programmes.versionsOf(programmeId);
        if (versions.isEmpty()) {
            throw BusinessException.unknown("programmeId", "programme");
        }
        List<Long> versionIds = versions.stream().map(ProgrammeVersionView::id).toList();
        int threshold = Math.max(POPULATION_FLOOR,
                config.intValue(ConfigKeys.REPORTING, ConfigKeys.MIN_POPULATION_THRESHOLD, POPULATION_FLOOR));
        boolean employer = EMPLOYER.equals(audience);

        EnrolmentCounts counts = enrolments.countsForProgramme(programmeId);
        Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("identified_population", figure(counts.invited()));
        figures.put("consent_funnel", figure(Map.of("invited", counts.invited(), "consented", counts.consented(),
                "activated", counts.activated(), "withdrawn", counts.withdrawn())));
        figures.put("plans_activated", figure(carePlans.countActive(versionIds)));
        figures.put("gap_closure", figure(Map.of(
                "open", careGaps.count(versionIds, List.of("detected", "assigned")),
                "closed", careGaps.count(versionIds, List.of("closed")))));

        List<String> withheld = new ArrayList<>();
        Map<Long, OutcomeDefinitionView> definitions = programmes.outcomeDefinitions(versionIds).stream()
                .collect(Collectors.toMap(OutcomeDefinitionView::id, Function.identity()));
        List<Map<String, Object>> measures = new ArrayList<>();
        if (!definitions.isEmpty()) {
            for (OutcomeObservationRepository.MeasureAggregate aggregate : outcomes.aggregate(definitions.keySet())) {
                OutcomeDefinitionView definition = definitions.get(aggregate.getDefinitionId());
                if (employer && aggregate.getN() < threshold) {
                    withheld.add("outcome:" + definition.measureCode() + ":v" + definition.measureVersion());
                    continue;
                }
                Map<String, Object> measure = new LinkedHashMap<>();
                measure.put("measureCode", definition.measureCode());
                measure.put("measureVersion", definition.measureVersion());
                measure.put("name", definition.name());
                measure.put("category", definition.category());
                measure.put("n", aggregate.getN());
                measure.put("mean", aggregate.getMean() == null ? null
                        : BigDecimal.valueOf(aggregate.getMean()).setScale(4, RoundingMode.HALF_UP));
                measures.add(measure);
            }
        }
        figures.put("outcome_measures", figure(measures));

        boolean populationTooSmall = employer && counts.activated() < threshold;
        if (populationTooSmall) {
            withheld.clear();
            withheld.addAll(figures.keySet().stream().sorted().toList());
            figures.clear();
        }

        LocalDate asAt = LocalDate.now();
        ProgrammeVersionView reportedVersion = versions.stream().filter(ProgrammeVersionView::isPublished)
                .findFirst().orElse(versions.get(0));
        ReportSnapshot snapshot = snapshots.save(new ReportSnapshot(reportedVersion.id(), audience, asAt, asAt,
                figures, threshold, withheld, ATTRIBUTION, Times.now()));
        audit.record(AuditEntry.of(OutcomeOperations.VIEW_PROGRAMME_PROOF, "report_snapshot", snapshot.getId())
                .programmeVersion(reportedVersion.id())
                .newState(Map.of("audience", audience, "withheldMeasures", withheld)));

        ProgrammeProof proof = new ProgrammeProof(programmeId, audience, asAt, threshold, figures, withheld,
                ATTRIBUTION, snapshot.getId());
        ServiceResult<ProgrammeProof> result = ServiceResult.of(proof).next("proceed")
                .session("programmeId", programmeId).session("reportSnapshotId", snapshot.getId());
        if (populationTooSmall) {
            result.warning(ZCareExceptionCode.ZCARE_AGGREGATE_THRESHOLD_NOT_MET,
                    "Figures withheld: the population is below the reporting threshold",
                    Map.of("threshold", threshold));
        }
        return result;
    }

    /** Every figure carries its measure version (UC-ZC-008 D3). */
    private static Map<String, Object> figure(Object value) {
        Map<String, Object> figure = new LinkedHashMap<>();
        figure.put("value", value);
        figure.put("measureVersion", 1);
        return figure;
    }
}

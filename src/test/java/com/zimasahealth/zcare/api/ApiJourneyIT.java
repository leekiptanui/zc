package com.zimasahealth.zcare.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimasahealth.zcare.support.ApiClient;
import com.zimasahealth.zcare.support.ApiClient.Response;
import com.zimasahealth.zcare.support.ApiTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The 04B founding slice end to end, through HTTP, on PostgreSQL 16 with the service connected
 * as {@code zc_app}: the envelope, the consent gate, the clinical boundaries, idempotency,
 * tenant isolation and the security responses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiJourneyIT {

    private static final String TENANT = "acme-health";
    private static final String OTHER_TENANT = "other-health";
    private static final String GATE_TENANT = "gate-health";
    private static final ApiTestDatabase DATABASE = ApiTestDatabase.create("api_journey_it");

    private String adminToken;
    private String clinicianToken;
    private String careManagerToken;
    private String platformToken;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::jdbcUrl);
        registry.add("spring.datasource.username", () -> ApiTestDatabase.APP_ROLE);
        registry.add("spring.datasource.password", DATABASE::appPassword);
        registry.add("spring.liquibase.url", DATABASE::jdbcUrl);
        registry.add("spring.liquibase.user", DATABASE::owner);
        registry.add("spring.liquibase.password", DATABASE::ownerPassword);
        registry.add("zcare.security.jwt.hmac-secret", () -> ApiClient.HMAC_SECRET);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper json;

    private ApiClient api;
    private long tenantId;

    @BeforeEach
    void tenants() {
        tenantId = DATABASE.ensureTenant(TENANT);
        DATABASE.ensureTenant(OTHER_TENANT);
        DATABASE.ensureTenant(GATE_TENANT);
        api = new ApiClient(rest, json);
        // Minted per test: context start-up can outlast a token minted when the class loads.
        adminToken = ApiClient.token("pa-1", "programme_admin", TENANT, null);
        clinicianToken = ApiClient.token("cl-1", "clinician", TENANT, null);
        careManagerToken = ApiClient.token("cm-1", "care_manager", TENANT, null);
        platformToken = ApiClient.token("pl-1", "platform_admin", TENANT, null);
    }

    @Test
    void carriesAMemberFromProgrammeToProof() {
        Fixture f = enrolActiveMember(api, adminToken, clinicianToken, careManagerToken, platformToken);

        Response assigned = ok(api.post("/enrolments/" + f.enrolmentId + "/assessments", careManagerToken,
                Map.of("templateCode", f.templateCode)));
        Response scored = ok(api.post("/assessments/" + assigned.id("id") + ":complete", careManagerToken,
                Map.of("answers", Map.of("q1", "yes", "q2", "yes"))));
        assertThat(scored.data().path("priorityTier").asText()).isEqualTo("high_priority");

        Response plan = ok(api.post("/care-plans", careManagerToken, Map.of("enrolmentId", f.enrolmentId,
                "goals", List.of(Map.of("goalTypeCode", "GLYCAEMIC", "description", "HbA1c below 7",
                        "targetValue", 7, "targetUnit", "%")),
                "interventions", List.of(Map.of("description", "Monthly call", "ownerRole", "care_manager")))));
        long planId = plan.id("id");
        assertThat(plan.body().path("nextActions").get(0).asText()).isEqualTo("submit_for_approval");
        ok(api.post("/care-plans/" + planId + ":submit", careManagerToken, null));
        assertThat(api.post("/care-plans/" + planId + ":activate", careManagerToken, null).status()).isEqualTo(403);
        Response active = ok(api.post("/care-plans/" + planId + ":activate", clinicianToken, null));
        assertThat(active.data().path("status").asText()).isEqualTo("active");

        Response queue = ok(api.get("/work-queues/care_manager?page=1&pageSize=10", careManagerToken));
        assertThat(queue.data().path("page").asInt()).isEqualTo(1);
        JsonNode interventionTask = first(queue.data().path("items"), "taskType", "CARE_PLAN_INTERVENTION");
        ok(api.post("/tasks/" + interventionTask.path("id").asLong() + ":complete", careManagerToken,
                Map.of("completionNote", "called")));

        Response breach = api.post("/observations", careManagerToken, observation(f.enrolmentId, 11.4));
        assertException(breach, "ZCARE_THRESHOLD_REVIEW_REQUIRED");
        assertThat(breach.data().path("reviewTaskId").asLong()).isPositive();
        assertException(api.post("/tasks/" + breach.data().path("reviewTaskId").asLong() + ":complete",
                careManagerToken, null), "ZCARE_TASK_RESTRICTED");

        Map<String, Object> gap = Map.of("enrolmentId", f.enrolmentId, "gapType", "HBA1C_OVERDUE",
                "periodKey", "2026-Q4", "detectReason", "No HbA1c in 90 days", "evaluatedData", Map.of("days", 120));
        long gapId = ok(api.post("/care-gaps", careManagerToken, gap)).id("id");
        assertException(api.post("/care-gaps", careManagerToken, gap), "ZCARE_GAP_DUPLICATE");
        assertException(api.post("/care-gaps/" + gapId + ":suppress", careManagerToken, Map.of()),
                "ZCARE_GAP_SUPPRESS_REASON_REQUIRED");
        ok(api.post("/care-gaps/" + gapId + ":suppress", careManagerToken, Map.of("reason", "Test booked")));

        Response context = ok(api.get("/members/" + f.memberId + "/care-context", careManagerToken));
        assertThat(context.data().path("healthContext").path("carePlan").path("activePlan").path("carePlanId")
                .asLong()).isEqualTo(planId);

        ok(api.post("/outcomes:record", adminToken, Map.of("programmeVersionId", f.programmeVersionId,
                "measureCode", "HBA1C_MEAN", "periodStart", "2026-07-01", "periodEnd", "2026-09-30", "value", 8.2)));
        Response proof = ok(api.get("/programmes/" + f.programmeId + "/proof?audience=payer", adminToken));
        assertThat(proof.data().path("figures").path("plans_activated").path("value").asLong()).isEqualTo(1);
        String employer = ApiClient.token("em-1", "employer_sponsor", TENANT, null);
        Response withheld = api.get("/programmes/" + f.programmeId + "/proof?audience=employer_aggregate", employer);
        assertException(withheld, "ZCARE_AGGREGATE_THRESHOLD_NOT_MET");
        assertThat(withheld.data().path("figures").isEmpty()).isTrue();

        Response audit = ok(api.get("/audit?filter[memberId]=" + f.memberId, platformToken));
        assertThat(audit.data().path("totalItems").asLong()).isGreaterThan(5);
        assertThat(audit.data().path("items").get(0).has("newState")).isFalse();
    }

    @Test
    void refusesPublicationWithoutClinicalApprovalAndFreezesThePublishedVersion() {
        long programmeId = ok(api.post("/programmes", adminToken, programme("FRZ"))).id("programmeId");
        Response refused = api.post("/programmes/" + programmeId + "/publish", adminToken, null);
        assertException(refused, "ZCARE_CLINICAL_APPROVAL_REQUIRED");
        assertThat(refused.body().path("nextActions")).isEmpty();
        assertThat(api.post("/programmes/" + programmeId + ":record-clinical-approval", adminToken, null).status())
                .isEqualTo(403);

        ok(api.post("/programmes/" + programmeId + ":record-clinical-approval", clinicianToken, null));
        ok(api.post("/programmes/" + programmeId + "/publish", adminToken, null));
        assertException(api.post("/programmes/" + programmeId + "/goal-types", adminToken,
                Map.of("code", "LATE", "name", "Late")), "ZCARE_PROGRAMME_VERSION_IMMUTABLE");
    }

    @Test
    void keepsAssistedConsentCapturedUntilPrivacyApprovesTheScript() {
        ApiClient gate = new ApiClient(rest, json);
        String admin = ApiClient.token("pa-g", "programme_admin", GATE_TENANT, null);
        String clinician = ApiClient.token("cl-g", "clinician", GATE_TENANT, null);
        String careManager = ApiClient.token("cm-g", "care_manager", GATE_TENANT, null);
        String platform = ApiClient.token("pl-g", "platform_admin", GATE_TENANT, null);
        Fixture f = invitedMember(gate, admin, clinician, careManager);

        Map<String, Object> consent = Map.of("wordingVersion", "v1", "channel", "assisted_call",
                "scopeContentClasses", List.of("condition_neutral", "health_content"));
        Response captured = gate.post("/enrolments/" + f.enrolmentId + "/consent:capture", careManager, consent);
        assertException(captured, "ZCARE_CONSENT_REQUIRED");
        assertThat(captured.data().path("consentState").asText()).isEqualTo("captured");
        assertThat(captured.data().path("privacyApprovalPending").asBoolean()).isTrue();

        Response blocked = gate.post("/enrolments/" + f.enrolmentId + ":activate", careManager, activation());
        assertException(blocked, "ZCARE_CONSENT_REQUIRED");
        assertThat(blocked.body().path("nextActions").get(0).asText()).isEqualTo("request_consent");
        assertThat(blocked.data().path("state").asText()).isEqualTo("invited");

        ok(gate.post("/config", platform, privacyApproval()));
        ok(gate.post("/enrolments/" + f.enrolmentId + "/consent:capture", careManager, consent));
        Response active = ok(gate.post("/enrolments/" + f.enrolmentId + ":activate", careManager, activation()));
        assertThat(active.body().path("nextActions").toString()).contains("create_care_plan");
    }

    @Test
    void withdrawalWithRevocationStopsAllContactAndClosesOpenWork() {
        Fixture f = enrolActiveMember(api, adminToken, clinicianToken, careManagerToken, platformToken);
        ok(api.post("/outreach-requests", careManagerToken, Map.of("memberId", f.memberId, "enrolmentId", f.enrolmentId,
                "contentClass", "health_content", "purpose", "reminder")));
        ok(api.post("/tasks", careManagerToken, Map.of("enrolmentId", f.enrolmentId, "taskType", "CALL",
                "assignedToRole", "care_manager")));

        ok(api.post("/enrolments/" + f.enrolmentId + ":withdraw", careManagerToken,
                Map.of("reason", "member_request", "revokeConsent", true, "verbatim", "STOP")));

        Response context = api.get("/members/" + f.memberId + "/care-context?enrolmentId=" + f.enrolmentId,
                careManagerToken);
        assertException(context, "ZCARE_CONSENT_REVOKED");
        assertThat(context.body().path("nextActions")).isEmpty();
        assertThat(context.data().path("healthContext").isNull()).isTrue();
        assertException(api.post("/outreach-requests", careManagerToken, Map.of("memberId", f.memberId,
                "enrolmentId", f.enrolmentId, "contentClass", "condition_neutral", "purpose", "x")),
                "ZCARE_CONSENT_REVOKED");

        assertThat(DATABASE.count(tenantId, "SELECT count(*) FROM zc_task WHERE enrolment_id = " + f.enrolmentId
                + " AND task_type = 'CALL' AND status = 'cancelled' AND cancel_reason = 'consent_revoked'"))
                .isEqualTo(1);
        assertThat(DATABASE.count(tenantId, "SELECT count(*) FROM zc_task WHERE enrolment_id = " + f.enrolmentId
                + " AND task_type = 'SAFE_EXIT' AND status = 'assigned'")).isEqualTo(1);
        assertThat(DATABASE.count(tenantId, "SELECT count(*) FROM zc_outreach_request WHERE enrolment_id = "
                + f.enrolmentId + " AND status = 'cancelled'")).isEqualTo(1);
    }

    @Test
    void replaysTheCachedEnvelopeAndRefusesAReusedKeyWithADifferentBody() {
        long programmeId;
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = programme("IDEM");
        Response first = ok(api.post("/programmes", adminToken, body, key));
        programmeId = first.id("programmeId");
        Response replay = api.post("/programmes", adminToken, body, key);
        assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.headers().getFirst("Idempotency-Replay")).isEqualTo("true");
        assertThat(replay.body().path("requestId")).isEqualTo(first.body().path("requestId"));
        assertThat(replay.id("programmeId")).isEqualTo(programmeId);

        assertException(api.post("/programmes", adminToken, programme("OTHER"), key), "IDEMPOTENCY_KEY_CONFLICT");
        assertException(api.exchange(HttpMethod.POST, "/programmes", adminToken, programme("NOKEY"), null),
                "VALIDATION_FIELD_REQUIRED");
    }

    @Test
    void answersAuthenticationAndAuthorisationFailuresOutsideTheEnvelope() {
        assertThat(api.get("/programmes", null).status()).isEqualTo(401);
        assertThat(api.get("/programmes", ApiClient.expiredToken("pa-1", "programme_admin", TENANT)).status())
                .isEqualTo(401);

        long deniedBefore = DATABASE.count(tenantId,
                "SELECT count(*) FROM zc_security_event WHERE event_type = 'authorisation_denied'");
        assertThat(api.post("/programmes", careManagerToken, programme("NOPE")).status()).isEqualTo(403);
        assertThat(api.get("/no-such-route", careManagerToken).status()).isEqualTo(403);
        assertThat(DATABASE.count(tenantId,
                "SELECT count(*) FROM zc_security_event WHERE event_type = 'authorisation_denied'"))
                .isEqualTo(deniedBefore + 2);

        // The tenant comes only from the token: naming another one in a header is refused and logged.
        assertThat(api.getWithHeader("/programmes", adminToken, "X-Tenant-Id", OTHER_TENANT).status())
                .isEqualTo(403);
        assertThat(DATABASE.count(tenantId,
                "SELECT count(*) FROM zc_security_event WHERE event_type = 'tenant_violation_attempt'"))
                .isPositive();
        assertThat(api.get("/programmes", ApiClient.token("pa-x", "programme_admin", "no-such-tenant", null))
                .status()).isEqualTo(403);
    }

    @Test
    void keepsHealthDataOutOfUrlsCachesAndReferrers() {
        Response response = ok(api.get("/programmes", adminToken));

        assertThat(response.headers().getCacheControl()).contains("no-store");
        assertThat(response.headers().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.body().toString()).doesNotContain(TENANT);
        assertThat(api.exchange(HttpMethod.GET, "//" + TENANT + "/zcare/v1/programmes", adminToken, null, null)
                .status()).isEqualTo(403);
    }

    @Test
    void neverShowsOneTenantAnotherTenantsRecords() {
        long programmeId = ok(api.post("/programmes", adminToken, programme("ISO"))).id("programmeId");
        String otherAdmin = ApiClient.token("pa-o", "programme_admin", OTHER_TENANT, null);

        Response foreign = api.get("/programmes/" + programmeId, otherAdmin);
        assertException(foreign, "VALIDATION_FIELD_INVALID");
        assertThat(ok(api.get("/programmes", otherAdmin)).data().path("totalItems").asLong()).isZero();
    }

    @Test
    void reportsEachInvalidFieldSeparately() {
        Response response = api.post("/programmes", adminToken, Map.of("name", ""));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.envelopeStatus()).isEqualTo("exception");
        assertThat(response.body().path("exceptions")).hasSize(4);
        assertThat(response.body().path("exceptions").findValuesAsText("field"))
                .containsExactlyInAnyOrder("code", "name", "careModel", "consentWordingVersion");
        assertThat(response.body().path("auditTrail").path("operation").asText()).isEqualTo("CREATE_PROGRAMME");
    }

    // ---- fixtures ----

    private record Fixture(long programmeId, long programmeVersionId, String templateCode, long memberId,
                           long enrolmentId) {
    }

    /** A published programme with vocabulary, a released cohort and an invited member. */
    private static Fixture invitedMember(ApiClient client, String admin, String clinician, String careManager) {
        Response created = ok(client.post("/programmes", admin, programme("P")));
        long programmeId = created.id("programmeId");
        long versionId = created.id("programmeVersionId");
        ok(client.post("/programmes/" + programmeId + "/observation-types", admin,
                Map.of("code", "HBA1C", "name", "HbA1c", "unit", "%", "plausibleMin", 3, "plausibleMax", 20)));
        ok(client.post("/programmes/" + programmeId + "/thresholds", admin,
                Map.of("observationTypeCode", "HBA1C", "reviewAbove", 9)));
        ok(client.post("/programmes/" + programmeId + "/goal-types", admin,
                Map.of("code", "GLYCAEMIC", "name", "Glycaemic control")));
        ok(client.post("/programmes/" + programmeId + "/gap-rules", admin, Map.of("gapType", "HBA1C_OVERDUE",
                "periodStrategy", "calendar_quarter", "definition", Map.of("maxAgeDays", 90))));
        ok(client.post("/programmes/" + programmeId + "/outcome-measures", admin, Map.of("measureCode", "HBA1C_MEAN",
                "name", "Mean HbA1c", "category", "clinical", "definition", Map.of("kind", "mean"))));
        ok(client.post("/programmes/" + programmeId + ":record-clinical-approval", clinician, null));
        ok(client.post("/programmes/" + programmeId + "/publish", admin, null));

        String templateCode = "RISK-" + suffix();
        long templateId = ok(client.post("/assessment-templates", admin, Map.of("code", templateCode,
                "name", "Risk screen",
                "questionnaire", Map.of("questions", List.of(Map.of("code", "q1"), Map.of("code", "q2"))),
                "scoringRules", Map.of("points", Map.of("q1", Map.of("yes", 5), "q2", Map.of("yes", 4)),
                        "tiers", List.of(Map.of("tier", "high_priority", "min", 8),
                                Map.of("tier", "standard", "min", 0)))))).id("templateId");
        ok(client.post("/assessment-templates/" + templateId + "/publish", admin, null));

        long cohortId = ok(client.post("/cohorts", admin, Map.of("programmeVersionId", versionId,
                "code", "C" + suffix(), "name", "Pilot"))).id("id");
        Response member = ok(client.post("/cohorts/" + cohortId + "/members", admin, Map.of(
                "member", Map.of("sourceSystem", "eagle", "sourceMemberNumber", "S" + suffix(),
                        "sourceIndividualRef", "01", "displayName", "Synthetic Member"),
                "inclusionReason", "manual pilot inclusion")));
        ok(client.post("/cohorts/" + cohortId + ":release", admin, null));
        long enrolmentId = ok(client.post("/enrolments:invite", careManager,
                Map.of("cohortMembershipId", member.id("id")))).id("id");
        return new Fixture(programmeId, versionId, templateCode, member.id("memberId"), enrolmentId);
    }

    /** {@link #invitedMember} plus validated consent and activation. */
    private static Fixture enrolActiveMember(ApiClient client, String admin, String clinician, String careManager,
                                             String platform) {
        Fixture f = invitedMember(client, admin, clinician, careManager);
        ok(client.post("/config", platform, privacyApproval()));
        ok(client.post("/enrolments/" + f.enrolmentId + "/consent:capture", careManager, Map.of(
                "wordingVersion", "v1", "channel", "assisted_call",
                "scopeContentClasses", List.of("condition_neutral", "health_content"))));
        ok(client.post("/enrolments/" + f.enrolmentId + ":activate", careManager, activation()));
        return f;
    }

    private static Map<String, Object> programme(String prefix) {
        return Map.of("code", prefix + "-" + suffix(), "name", "Diabetes (synthetic)",
                "careModel", "chronic_disease_management", "consentWordingVersion", "v1");
    }

    private static Map<String, Object> activation() {
        return Map.of("responsibility", Map.of("careManagerId", "cm-1"));
    }

    private static Map<String, Object> privacyApproval() {
        return Map.of("scope", "privacy", "key", "assisted_consent_privacy_approved", "value", "true",
                "changeReason", "Privacy approved the assisted script (synthetic)");
    }

    private static Map<String, Object> observation(long enrolmentId, double value) {
        return Map.of("enrolmentId", enrolmentId, "observationTypeCode", "HBA1C", "value", value, "unit", "%",
                "observedAt", Instant.now().minusSeconds(60).toString(), "source", "care_team_recorded");
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Response ok(Response response) {
        assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
        assertThat(response.envelopeStatus()).as(response.body().toString()).isEqualTo("success");
        assertThat(response.body().path("requestId").asText()).isNotBlank();
        assertThat(response.body().path("auditTrail").path("operation").asText()).isNotBlank();
        return response;
    }

    private static void assertException(Response response, String code) {
        assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
        assertThat(response.envelopeStatus()).as(response.body().toString()).isEqualTo("exception");
        assertThat(response.body().path("exceptions").findValuesAsText("code")).as(response.body().toString())
                .contains(code);
    }

    private static JsonNode first(JsonNode items, String field, String value) {
        for (JsonNode item : items) {
            if (value.equals(item.path(field).asText())) {
                return item;
            }
        }
        throw new AssertionError("No item with " + field + "=" + value + " in " + items);
    }
}

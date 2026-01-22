package uk.gov.moj.cpp.staging.dcs.it;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.List.of;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.hmcts.dcs.openapi.model.ErrorResponsePayload.ErrorCodeEnum.CASE_DELETED;
import static uk.gov.hmcts.dcs.openapi.model.ErrorResponsePayload.ErrorCodeEnum.CASE_HAS_SPLIT_OR_MERGED;
import static uk.gov.hmcts.dcs.openapi.model.ErrorResponsePayload.ErrorCodeEnum.CASE_NOT_FOUND;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.RETRY;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.SENT;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType.DEFENDANT_UPDATE;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType.MATERIAL_UPDATE;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsApiAddMaterialCall;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsApiAddMaterialCall_500Error;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsCreateCallOnSuccess;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsCreateCallOnSuccess_multipleDefendants;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsCreateCall_On500Error;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsDefendantsDefenceUpdateCal;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsDefendantsDefenceUpdateCalOn404Response;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsDefendantsDefenceUpdateCalOn500Response;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsDefendantsUpdateCal;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsDefendantsUpdateCal_500Error;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsDefendantsUpdateCall_WithErrorCode;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsErrorWhenDefendantsUpdateCal;
import static uk.gov.moj.cpp.staging.dcs.stub.DefenceServiceStub.stubDefenceService;
import static uk.gov.moj.cpp.staging.dcs.stub.ProgressionServiceStub.stubProgressionService;
import static uk.gov.moj.cpp.staging.dcs.stub.ProgressionServiceStub.stubProgressionServiceForRelatedCases;
import static uk.gov.moj.cpp.staging.dcs.stub.ProgressionServiceStub.stubProgressionServiceWith2Defendant;
import static uk.gov.moj.cpp.staging.dcs.stub.ReferenceDataServiceStub.getDocumentTypeAccessByIdStub;
import static uk.gov.moj.cpp.staging.dcs.stub.ReferenceDataServiceStub.prosecutorByProsecutionAuthorityStub;
import static uk.gov.moj.cpp.staging.dcs.stub.UsersGroupsStub.stubUserGoupsService;
import static uk.gov.moj.cpp.staging.dcs.util.Constants.CONTEXT;
import static uk.gov.moj.cpp.staging.dcs.util.Constants.FEATURE_STAGING_DCS;
import static uk.gov.moj.cpp.staging.dcs.util.QueueUtil.sendDefencePublicEvent;
import static uk.gov.moj.cpp.staging.dcs.util.QueueUtil.sendPublicEvent;
import static uk.gov.moj.cpp.staging.dcs.util.QueueUtil.verifyPublicEventCaseDefendantChanged;
import static uk.gov.moj.cpp.staging.dcs.util.QueueUtil.verifyPublicEventConsumerForDefenceOrganisationAssociated;
import static uk.gov.moj.cpp.staging.dcs.util.QueueUtil.verifyPublicEventConsumerForDefenceOrganisationDisassociated;
import static uk.gov.moj.cpp.staging.dcs.util.QueueUtil.verifyPublicProgressionCourtDocumentAdded;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.helper.DcsHelper;
import uk.gov.moj.cpp.staging.dcs.helper.QueryHelper;
import uk.gov.moj.cpp.staging.dcs.stub.ApiRestEndpoint;
import uk.gov.moj.cpp.staging.dcs.stub.SimpleRestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.core.Response;

import com.google.common.collect.ImmutableMap;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StagingDcsIT extends BaseIT {
    public static final String LINK_DEFENDANT = "LINK_DEFENDANT";
    public static final String DEFENCE_REPRESENTATION = "DEFENCE_REPRESENTATION";
    public static final String SUCCESS = "SUCCESS";
    public static final String PROSECUTION_CASE_ID = "PROSECUTION_CASE_ID";
    private final DcsHelper dcsHelper = new DcsHelper();
    private final QueryHelper queryHelper = new QueryHelper();
    private static final String DCS_LINK_SEVERED = "DCS LINK SEVERED";
    public static final String PUBLIC_PROGRESSION_CASE_DEFENDANT_CHANGED = "public.progression.case-defendant-changed";
    public static final String PUBLIC_PROGRESSION_EVENTS_COURT_DOCUMENT_CREATED = "public.progression.events.court-document-created";
    public static final String DEFENCE_ASSOCIATED_EVENT = "public.defence.defence-organisation-associated";
    public static final String DEFENCE_DISASSOCIATED_EVENT = "public.defence.defence-organisation-disassociated";

    @BeforeEach
    void eachSetUp() throws IOException, InterruptedException {
        setup();
        prosecutorByProsecutionAuthorityStub("CPS");
        createSendMaterialFunctionalStubs(caseId.toString(), defendantId.toString(), defendantId.toString(), caseUrn);
        stubDcsApiAddMaterialCall(caseUrn);
    }

    @Test
    void shouldLinkCaseAndDefendant() {
        FeatureStubber.stubFeaturesFor(CONTEXT, ImmutableMap.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());
        stubProgressionService(caseId);

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        //Adding second defendant on the same case.
        final UUID defendantId2 = randomUUID();
        final UUID defendantReferral2 = randomUUID();
        stubDcsCreateCallOnSuccess(caseId, defendantId2, caseUrn, defendantReferral2, caseReferral);
        final JsonObject payload2 = dcsHelper.createCaseinDcsRequest(caseId, defendantId2, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode2 = sendCommandToAddRequest(payload2);
        assertThat(responseStatusCode2, is(ACCEPTED.getStatusCode()));
        queryHelper.queryCaseDetailByCaseIdAndAssertMatch(caseId,
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantStatus", equalTo(LINKED)),
                withJsonPath("$.defendants[1].defendantId", equalTo(defendantId2.toString())),
                withJsonPath("$.defendants[1].defendantStatus", equalTo(LINKED)),
                withJsonPath("$.caseStatus", equalTo(LINKED)));
    }

    @Test
    void shouldLinkCaseAndDefendant_AfterRetry() {
        FeatureStubber.stubFeaturesFor(CONTEXT, ImmutableMap.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCall_On500Error(caseUrn);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());
        stubProgressionService(caseId);

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", TransactionType.LINK_DEFENDANT.toString(), "transactionStatus", TransactionStatus.RETRY.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", equalTo(LINK_DEFENDANT)),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionStatus", equalTo(TransactionStatus.RETRY.toString())));

        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);
    }

    @Test
    void shouldLinkCaseAndDefendantOrganisation() {
        FeatureStubber.stubFeaturesFor(CONTEXT, ImmutableMap.of(FEATURE_STAGING_DCS, TRUE));

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record-for-organisation.json");
        String organisationId = randomUUID().toString();
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubDcsDefendantsDefenceUpdateCal(caseUrn, defendantReferral.toString());
        stubProgressionService(caseId);
        stubDefenceService(defendantId.toString(), organisationId);
        stubUserGoupsService(organisationId);

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        final String metaDataPayload = queryHelper.queryTransactionMetadataAndAssertMatch(caseId, null,
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(2)),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", anyOf(equalTo(LINK_DEFENDANT), equalTo(DEFENCE_REPRESENTATION))),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionStatus", equalTo(SUCCESS))
        );

        final List<UUID> tranIdList = getAllTransactionIdsFromStringResponse(metaDataPayload);
        final UUID linkedTranIdOne = tranIdList.get(0);
        final UUID linkedTranIdTwo = tranIdList.get(1);

        queryHelper.queryTransactionDetailsAndAssertMatch(tranIdList,
                withJsonPath("$.transactionsDetails", hasSize(2)),
                withJsonPath("$.transactionsDetails[0].transactionId", anyOf(equalTo(linkedTranIdOne.toString()), equalTo(linkedTranIdTwo.toString()))),
                withJsonPath("$.transactionsDetails[0].transactionType", anyOf(equalTo(LINK_DEFENDANT), equalTo(DEFENCE_REPRESENTATION))),
                withJsonPath("$.transactionsDetails[0].transactionStatus", equalTo(SUCCESS)),
                withJsonPath("$.transactionsDetails[1].transactionId", anyOf(equalTo(linkedTranIdOne.toString()), equalTo(linkedTranIdTwo.toString()))),
                withJsonPath("$.transactionsDetails[1].transactionType",  anyOf(equalTo(LINK_DEFENDANT), equalTo(DEFENCE_REPRESENTATION))),
                withJsonPath("$.transactionsDetails[1].transactionStatus", equalTo(SUCCESS)),
                withJsonPath("$.transactionsDetails[0].payload", notNullValue())
                );

        sendPublicEvent("public.progression.events.case-or-application-ejected", "stub-data/public.progression.events.case-or-application-ejected.json", Map.of(PROSECUTION_CASE_ID, caseId.toString()));
        verifyCaseStatusLinked(caseId, DCS_LINK_SEVERED, defendantId, NOT_LINKED);
    }

    @Test
    void shouldLinkCaseAndDefendantOrganisation_RetrySuccess() {
        FeatureStubber.stubFeaturesFor(CONTEXT, ImmutableMap.of(FEATURE_STAGING_DCS, TRUE));
        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record-for-organisation.json");
        String organisationId = randomUUID().toString();
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubDcsDefendantsDefenceUpdateCalOn500Response(caseUrn, defendantReferral.toString(), "SERVER_ERROR", randomUUID().toString());
        stubProgressionService(caseId);
        stubDefenceService(defendantId.toString(), organisationId);
        stubUserGoupsService(organisationId);

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", TransactionType.DEFENCE_REPRESENTATION.toString(), "transactionStatus", TransactionStatus.RETRY.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", equalTo(DEFENCE_REPRESENTATION)),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionStatus", equalTo(RETRY.name()))
        );

        stubDcsDefendantsDefenceUpdateCal(caseUrn, defendantReferral.toString());

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", TransactionType.DEFENCE_REPRESENTATION.toString(), "transactionStatus", TransactionStatus.SUCCESS.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", equalTo(DEFENCE_REPRESENTATION)),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionStatus", equalTo(SUCCESS))
        );
    }

    @Test
    void shouldUpdateDefendantUpdatesWhenCaseDefendantChanged() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId, defendantId);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        sendPublicEvent(PUBLIC_PROGRESSION_CASE_DEFENDANT_CHANGED,
                "stub-data/public.progression.case-defendant-changed.json", caseId.toString(), defendantId.toString());
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyPublicEventCaseDefendantChanged();
        verifyDefendantStatusUpdated(caseId, LINKED, defendantId, LINKED, DCS_UPDATED);
    }

    @Test
    void shouldUpdateDefendantUpdatesWhenCaseDefendantChanged_AfterRetrySuccess() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId, defendantId);
        stubDcsDefendantsUpdateCal_500Error(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        sendPublicEvent(PUBLIC_PROGRESSION_CASE_DEFENDANT_CHANGED,
                "stub-data/public.progression.case-defendant-changed.json", caseId.toString(), defendantId.toString());
        verifyPublicEventCaseDefendantChanged();

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", TransactionType.DEFENDANT_UPDATE.toString(), "transactionStatus", TransactionStatus.RETRY.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", equalTo(DEFENDANT_UPDATE.name())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionStatus", equalTo(RETRY.name())));

        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());
        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", TransactionType.DEFENDANT_UPDATE.toString(), "transactionStatus", TransactionStatus.SUCCESS.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", equalTo(DEFENDANT_UPDATE.name())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionStatus", equalTo(SUCCESS)));
        verifyDefendantStatusUpdated(caseId, LINKED, defendantId, LINKED, DCS_UPDATED);
    }

    @Test
    void shouldUnlinkCase_WhenDefendantUpdateIsRespondedWithSplitAndMergedError() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId, defendantId);
        stubDcsDefendantsUpdateCall_WithErrorCode(caseUrn, defendantReferral.toString(), CASE_HAS_SPLIT_OR_MERGED.getValue(), "case is merged", randomUUID().toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        sendPublicEvent(PUBLIC_PROGRESSION_CASE_DEFENDANT_CHANGED,
                "stub-data/public.progression.case-defendant-changed.json", caseId.toString(), defendantId.toString());
        verifyPublicEventCaseDefendantChanged();
        verifyDefendantStatusUpdated(caseId, DCS_LINK_SEVERED, defendantId, NOT_LINKED, DCS_NOT_UPDATED);
    }

    @Test
    void shouldUnlinkCase_WhenDefendantUpdateIsRespondedWithCaseDeletedError() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId, defendantId);
        stubDcsDefendantsUpdateCall_WithErrorCode(caseUrn, defendantReferral.toString(), CASE_DELETED.getValue(), "case deleted", randomUUID().toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        sendPublicEvent(PUBLIC_PROGRESSION_CASE_DEFENDANT_CHANGED,
                "stub-data/public.progression.case-defendant-changed.json", caseId.toString(), defendantId.toString());
        verifyPublicEventCaseDefendantChanged();
        verifyDefendantStatusUpdated(caseId, DCS_LINK_SEVERED, defendantId, NOT_LINKED, DCS_NOT_UPDATED);
    }

    @Test
    void shouldNotRetryOn404WhenDefendantUpdatesToDcsRequested() {

        String urn = generateUrn();
        UUID defendantRef = randomUUID();

        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        final UUID defId = randomUUID();
        final UUID prosecutionCaseId = randomUUID();
        final UUID caseReferralId = randomUUID();

        stubDcsCreateCallOnSuccess(prosecutionCaseId, defId, urn, defendantRef, caseReferralId);
        stubProgressionService(prosecutionCaseId, defId);
        stubDcsErrorWhenDefendantsUpdateCal(urn, defendantRef.toString(), CASE_NOT_FOUND.getValue(), CASE_NOT_FOUND.getValue(), randomUUID().toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(prosecutionCaseId, defId, urn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);

        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(prosecutionCaseId, LINKED, defId, LINKED);

        sendPublicEvent(PUBLIC_PROGRESSION_CASE_DEFENDANT_CHANGED,
                "stub-data/public.progression.case-defendant-changed-for-404.json", prosecutionCaseId.toString(), defId.toString());
        verifyPublicEventCaseDefendantChanged();
        verifyDefendantStatusUpdated(prosecutionCaseId, LINKED, defId, LINKED, DCS_NOT_UPDATED);
    }

    @Test
    void shouldUpdateMaterial_WhenCourtDocumentAdded_FailedByDcs() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId, defendantId);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        //This result depends on the stub data of document search
        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", "MATERIAL_UPDATE"),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(2)));

        //resetting stub data of document search, document type reference data
        documentTypeId1 = randomUUID();
        documentTypeId2 = randomUUID();
        documentTypeId3 = randomUUID();
        materialId1 = randomUUID();
        materialId2 = randomUUID();
        materialId3 = randomUUID();
        createSendMaterialFunctionalStubs(caseId.toString(), defendantId.toString(), null, caseUrn);
        getDocumentTypeAccessByIdStub(documentTypeId1.toString());

        Map<String, String> replacevalueMap = new HashMap<>();
        replacevalueMap.put("CASE_ID", caseId.toString());
        replacevalueMap.put("MATERIAL_ID", materialId1.toString());
        replacevalueMap.put("DOCUMENT_ACCESS_TYPE_ID", documentTypeId1.toString());
        sendPublicEvent(PUBLIC_PROGRESSION_EVENTS_COURT_DOCUMENT_CREATED,
                "stub-data/public.progression.events.court-document-created-case-level.json", replacevalueMap);
        verifyPublicProgressionCourtDocumentAdded();

        final String responsePayload = queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("materialId", materialId1.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations[0].materialId", equalTo(materialId1.toString())),
                withJsonPath("$.caseOperations[0].transactionType", equalTo(MATERIAL_UPDATE.name())),
                withJsonPath("$.caseOperations[0].transactionStatus", equalTo(SENT.name())));

       final JsonObject responseObject =  new StringToJsonObjectConverter().convert(responsePayload);
       final String transactionRef = responseObject.getJsonArray("caseOperations").getValuesAs(JsonObject.class).stream()
               .map(obj -> obj.getString("transactionId"))
               .findFirst().orElse(null);

       final int responseCode = sendCommandToProcessTransactionStatus(transactionRef, dcsHelper.processTransactionStatusRequest(caseId, defendantId,"json/transactionStatusErrorPayload.json"));
       assertThat(responseCode, is(HttpStatus.SC_ACCEPTED));

        queryHelper.queryTransactionDetailsAndAssertMatch(of(fromString(transactionRef)),
                withJsonPath("$.transactionsDetails", hasSize(1)),
                withJsonPath("$.transactionsDetails[0].transactionId", is(transactionRef)),
                withJsonPath("$.transactionsDetails[0].transactionType", is(MATERIAL_UPDATE.name())),
                withJsonPath("$.transactionsDetails[0].transactionStatus", equalTo(TransactionStatus.FAILED.name())));
    }

    @Test
    void shouldUnlinkCaseAtUpdateMaterial_FailedAsynchronouslyByDcsWithSplitAndMergeError() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId, defendantId);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        //This result depends on the stub data of document search
        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", "MATERIAL_UPDATE"),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(2)));

        //resetting stub data of document search, document type reference data
        documentTypeId1 = randomUUID();
        documentTypeId2 = randomUUID();
        documentTypeId3 = randomUUID();
        materialId1 = randomUUID();
        materialId2 = randomUUID();
        materialId3 = randomUUID();
        createSendMaterialFunctionalStubs(caseId.toString(), defendantId.toString(), null, caseUrn);
        getDocumentTypeAccessByIdStub(documentTypeId1.toString());

        Map<String, String> replacevalueMap = new HashMap<>();
        replacevalueMap.put("CASE_ID", caseId.toString());
        replacevalueMap.put("MATERIAL_ID", materialId1.toString());
        replacevalueMap.put("DOCUMENT_ACCESS_TYPE_ID", documentTypeId1.toString());
        sendPublicEvent(PUBLIC_PROGRESSION_EVENTS_COURT_DOCUMENT_CREATED,
                "stub-data/public.progression.events.court-document-created-case-level.json", replacevalueMap);
        verifyPublicProgressionCourtDocumentAdded();

        final String responsePayload = queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("materialId", materialId1.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations[0].materialId", equalTo(materialId1.toString())),
                withJsonPath("$.caseOperations[0].transactionType", equalTo(MATERIAL_UPDATE.name())),
                withJsonPath("$.caseOperations[0].transactionStatus", equalTo(SENT.name())));

        final JsonObject responseObject =  new StringToJsonObjectConverter().convert(responsePayload);
        final String transactionRef = responseObject.getJsonArray("caseOperations").getValuesAs(JsonObject.class).stream()
                .map(obj -> obj.getString("transactionId"))
                .findFirst().orElse(null);

        final int responseCode = sendCommandToProcessTransactionStatus(transactionRef, dcsHelper.processTransactionStatusRequest(caseId, defendantId,"json/transactionStatusErrorPayload-splitAndMerge.json"));
        assertThat(responseCode, is(HttpStatus.SC_ACCEPTED));

        queryHelper.queryTransactionDetailsAndAssertMatch(of(fromString(transactionRef)),
                withJsonPath("$.transactionsDetails", hasSize(1)),
                withJsonPath("$.transactionsDetails[0].transactionId", is(transactionRef)),
                withJsonPath("$.transactionsDetails[0].transactionType", is(MATERIAL_UPDATE.name())),
                withJsonPath("$.transactionsDetails[0].transactionStatus", equalTo(TransactionStatus.FAILED.name())));

        verifyCaseDefendantStatus(caseId, DCS_LINK_SEVERED, defendantId, NOT_LINKED);
    }

    @Test
    void shouldUpdateMaterial_WhenCourtDocumentAdded_ProcessedByDcs() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId, defendantId);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        //This result depends on the stub data of document search
        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", "MATERIAL_UPDATE"),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(2)));

        //resetting stub data of document search, document type reference data
        documentTypeId1 = randomUUID();
        documentTypeId2 = randomUUID();
        documentTypeId3 = randomUUID();
        materialId1 = randomUUID();
        materialId2 = randomUUID();
        materialId3 = randomUUID();
        createSendMaterialFunctionalStubs(caseId.toString(), defendantId.toString(), null, caseUrn);
        getDocumentTypeAccessByIdStub(documentTypeId1.toString());

        Map<String, String> replacevalueMap = new HashMap<>();
        replacevalueMap.put("CASE_ID", caseId.toString());
        replacevalueMap.put("MATERIAL_ID", materialId1.toString());
        replacevalueMap.put("DOCUMENT_ACCESS_TYPE_ID", documentTypeId1.toString());
        sendPublicEvent(PUBLIC_PROGRESSION_EVENTS_COURT_DOCUMENT_CREATED,
                "stub-data/public.progression.events.court-document-created-case-level.json", replacevalueMap);
        verifyPublicProgressionCourtDocumentAdded();

        final String responsePayload = queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("materialId", materialId1.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations[0].materialId", equalTo(materialId1.toString())),
                withJsonPath("$.caseOperations[0].transactionType", equalTo(MATERIAL_UPDATE.name())),
                withJsonPath("$.caseOperations[0].transactionStatus", equalTo(SENT.name())));

        final JsonObject responseObject =  new StringToJsonObjectConverter().convert(responsePayload);
        final String transactionRef = responseObject.getJsonArray("caseOperations").getValuesAs(JsonObject.class).stream()
                .map(obj -> obj.getString("transactionId"))
                .findFirst().orElse(null);

        final int responseCode = sendCommandToProcessTransactionStatus(transactionRef, dcsHelper.processTransactionStatusRequest(caseId, defendantId,"json/transactionStatusSuccessPayload.json"));
        assertThat(responseCode, is(HttpStatus.SC_ACCEPTED));

        queryHelper.queryTransactionDetailsAndAssertMatch(of(fromString(transactionRef)),
                withJsonPath("$.transactionsDetails", hasSize(1)),
                withJsonPath("$.transactionsDetails[0].transactionId", is(transactionRef)),
                withJsonPath("$.transactionsDetails[0].transactionType", is(MATERIAL_UPDATE.name())),
                withJsonPath("$.transactionsDetails[0].transactionStatus", equalTo(TransactionStatus.SUCCESS.name())));
    }

    @Test
    void shouldUpdateMaterial_ProcessedByDcsAfterRetry() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId, defendantId);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());
        stubDcsApiAddMaterialCall_500Error(caseUrn);

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        //This result depends on the stub data of document search
        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", "MATERIAL_UPDATE"),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(2)));

        //resetting stub data of document search, document type reference data

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("materialId", materialId1.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations[0].materialId", equalTo(materialId1.toString())),
                withJsonPath("$.caseOperations[0].transactionType", equalTo(MATERIAL_UPDATE.name())),
                withJsonPath("$.caseOperations[0].transactionStatus", equalTo(RETRY.name())));
        stubDcsApiAddMaterialCall(caseUrn);

        final String SuccessResponsePayload = queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("materialId", materialId1.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations[0].materialId", equalTo(materialId1.toString())),
                withJsonPath("$.caseOperations[0].transactionType", equalTo(MATERIAL_UPDATE.name())),
                withJsonPath("$.caseOperations[0].transactionStatus", equalTo(SENT.name())));

        final JsonObject responseObject =  new StringToJsonObjectConverter().convert(SuccessResponsePayload);
        final String transactionRef = responseObject.getJsonArray("caseOperations").getValuesAs(JsonObject.class).stream()
                .map(obj -> obj.getString("transactionId"))
                .findFirst().orElse(null);

        final int responseCode = sendCommandToProcessTransactionStatus(transactionRef, dcsHelper.processTransactionStatusRequest(caseId, defendantId,"json/transactionStatusSuccessPayload.json"));
        assertThat(responseCode, is(HttpStatus.SC_ACCEPTED));

        queryHelper.queryTransactionDetailsAndAssertMatch(of(fromString(transactionRef)),
                withJsonPath("$.transactionsDetails", hasSize(1)),
                withJsonPath("$.transactionsDetails[0].transactionId", is(transactionRef)),
                withJsonPath("$.transactionsDetails[0].transactionType", is(MATERIAL_UPDATE.name())),
                withJsonPath("$.transactionsDetails[0].transactionStatus", equalTo(TransactionStatus.SUCCESS.name())));
    }

    @Test
    void shouldNotSendMaterial_WhenDuplicateCourtDocumentAdded_ForSameCaseSameMaterial() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId, defendantId);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        //This result depends on the stub data of document search
        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", "MATERIAL_UPDATE"),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(2)));

        //resetting stub data of document search, document type reference data
        createSendMaterialFunctionalStubs(caseId.toString(), defendantId.toString(), null, caseUrn);
        getDocumentTypeAccessByIdStub(documentTypeId1.toString());

        Map<String, String> replacevalueMap = new HashMap<>();
        replacevalueMap.put("CASE_ID", caseId.toString());
        replacevalueMap.put("MATERIAL_ID", materialId1.toString());
        replacevalueMap.put("DOCUMENT_ACCESS_TYPE_ID", documentTypeId1.toString());
        sendPublicEvent(PUBLIC_PROGRESSION_EVENTS_COURT_DOCUMENT_CREATED,
                "stub-data/public.progression.events.court-document-created-case-level.json", replacevalueMap);
        verifyPublicProgressionCourtDocumentAdded();

        final String responsePayload = queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("materialId", materialId1.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations[0].materialId", equalTo(materialId1.toString())),
                withJsonPath("$.caseOperations[0].transactionType", equalTo(MATERIAL_UPDATE.name())),
                withJsonPath("$.caseOperations[0].transactionStatus", equalTo(SENT.name())));

        final JsonObject responseObject =  new StringToJsonObjectConverter().convert(responsePayload);
        final String transactionRef = responseObject.getJsonArray("caseOperations").getValuesAs(JsonObject.class).stream()
                .map(obj -> obj.getString("transactionId"))
                .findFirst().orElse(null);

        final int responseCode = sendCommandToProcessTransactionStatus(transactionRef, dcsHelper.processTransactionStatusRequest(caseId, defendantId,"json/transactionStatusSuccessPayload.json"));
        assertThat(responseCode, is(HttpStatus.SC_ACCEPTED));

        queryHelper.queryTransactionDetailsAndAssertMatch(of(fromString(transactionRef)),
                withJsonPath("$.transactionsDetails", hasSize(1)),
                withJsonPath("$.transactionsDetails[0].transactionId", is(transactionRef)),
                withJsonPath("$.transactionsDetails[0].transactionType", is(MATERIAL_UPDATE.name())),
                withJsonPath("$.transactionsDetails[0].transactionStatus", equalTo(TransactionStatus.SUCCESS.name())));

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", "MATERIAL_UPDATE"),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(2)));
    }

    @Test
    void shouldNotSendMaterial_WhenDuplicateCourtDocumentAdded_ForSameDefendantSameMaterial() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId, defendantId);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        //This result depends on the stub data of document search
        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", "MATERIAL_UPDATE"),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(2)));

        //resetting stub data of document search, document type reference data
        createSendMaterialFunctionalStubs(caseId.toString(), defendantId.toString(), null, caseUrn);
        getDocumentTypeAccessByIdStub(documentTypeId1.toString());

        Map<String, String> replacevalueMap = new HashMap<>();
        replacevalueMap.put("CASE_ID", caseId.toString());
        replacevalueMap.put("DEFENDANT_ID1", defendantId.toString());
        replacevalueMap.put("MATERIAL_ID", materialId2.toString());
        replacevalueMap.put("DOCUMENT_ACCESS_TYPE_ID", documentTypeId1.toString());
        sendPublicEvent(PUBLIC_PROGRESSION_EVENTS_COURT_DOCUMENT_CREATED,
                "stub-data/public.progression.events.court-document-created-defendant-level.json", replacevalueMap);
        verifyPublicProgressionCourtDocumentAdded();

        final String responsePayload = queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("materialId", materialId1.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations[0].materialId", equalTo(materialId1.toString())),
                withJsonPath("$.caseOperations[0].transactionType", equalTo(MATERIAL_UPDATE.name())),
                withJsonPath("$.caseOperations[0].transactionStatus", equalTo(SENT.name())));

        final JsonObject responseObject =  new StringToJsonObjectConverter().convert(responsePayload);
        final String transactionRef = responseObject.getJsonArray("caseOperations").getValuesAs(JsonObject.class).stream()
                .map(obj -> obj.getString("transactionId"))
                .findFirst().orElse(null);

        final int responseCode = sendCommandToProcessTransactionStatus(transactionRef, dcsHelper.processTransactionStatusRequest(caseId, defendantId,"json/transactionStatusSuccessPayload.json"));
        assertThat(responseCode, is(HttpStatus.SC_ACCEPTED));

        queryHelper.queryTransactionDetailsAndAssertMatch(of(fromString(transactionRef)),
                withJsonPath("$.transactionsDetails", hasSize(1)),
                withJsonPath("$.transactionsDetails[0].transactionId", is(transactionRef)),
                withJsonPath("$.transactionsDetails[0].transactionType", is(MATERIAL_UPDATE.name())),
                withJsonPath("$.transactionsDetails[0].transactionStatus", equalTo(TransactionStatus.SUCCESS.name())));

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", "MATERIAL_UPDATE"),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(2)));
    }

    @Test
    void shouldSendMaterial_WhenCourtDocumentAdded_WithMasterDefendantId() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        final UUID masterDefendantId = randomUUID();
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionServiceForRelatedCases(caseId, defendantId, masterDefendantId);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        getDocumentTypeAccessByIdStub(documentTypeId1.toString());
        Map<String, String> replacevalueMap = new HashMap<>();
        replacevalueMap.put("CASE_ID", caseId.toString());
        replacevalueMap.put("DEFENDANT_ID", masterDefendantId.toString());
        replacevalueMap.put("MATERIAL_ID", materialId1.toString());
        replacevalueMap.put("DOCUMENT_ACCESS_TYPE_ID", documentTypeId1.toString());
        sendPublicEvent(PUBLIC_PROGRESSION_EVENTS_COURT_DOCUMENT_CREATED,
                "stub-data/public.progression.events.court-document-created-defendant-level.json", replacevalueMap);
        verifyPublicProgressionCourtDocumentAdded();

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("defendantId", defendantId.toString(), "materialId", materialId1.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].materialId", equalTo(materialId1.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", equalTo(MATERIAL_UPDATE.name())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionStatus", equalTo(SENT.name())));
    }

    @Test
    void shouldSendMaterial_WhenCourtDocumentAdded_WithDefendantId() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId, defendantId);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        getDocumentTypeAccessByIdStub(documentTypeId1.toString());
        Map<String, String> replacevalueMap = new HashMap<>();
        replacevalueMap.put("CASE_ID", caseId.toString());
        replacevalueMap.put("DEFENDANT_ID", defendantId.toString());
        replacevalueMap.put("MATERIAL_ID", materialId1.toString());
        replacevalueMap.put("DOCUMENT_ACCESS_TYPE_ID", documentTypeId1.toString());
        sendPublicEvent(PUBLIC_PROGRESSION_EVENTS_COURT_DOCUMENT_CREATED,
                "stub-data/public.progression.events.court-document-created-defendant-level.json", replacevalueMap);
        verifyPublicProgressionCourtDocumentAdded();

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("defendantId", defendantId.toString(), "materialId", materialId1.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].materialId", equalTo(materialId1.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", equalTo(MATERIAL_UPDATE.name())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionStatus", equalTo(SENT.name())));
    }

    @Test
    void shouldSendMaterial_WhenDuplicateCourtDocumentAdded_ForAdditionalDefendantSameMaterial() {
        final UUID defendantId2 = randomUUID();
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess_multipleDefendants(caseId, List.of(defendantId,defendantId2), caseUrn, defendantReferral, caseReferral);
        stubProgressionServiceWith2Defendant(caseId, defendantId, defendantId2);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());



        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, defendantId2, caseUrn, "json/stagingdcs.submit-dcs-case-record-two-defendant.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        //This result depends on the stub data of document search
        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", "MATERIAL_UPDATE"),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(2)));

        //resetting stub data of document search, document type reference data
        createSendMaterialFunctionalStubs(caseId.toString(), defendantId.toString(), null, caseUrn);
        getDocumentTypeAccessByIdStub(documentTypeId1.toString());

        Map<String, String> replacevalueMap = new HashMap<>();
        replacevalueMap.put("CASE_ID", caseId.toString());
        replacevalueMap.put("DEFENDANT_ID1", defendantId.toString());
        replacevalueMap.put("DEFENDANT_ID2", defendantId2.toString());
        replacevalueMap.put("MATERIAL_ID", materialId2.toString());
        replacevalueMap.put("DOCUMENT_ACCESS_TYPE_ID", documentTypeId1.toString());
        sendPublicEvent(PUBLIC_PROGRESSION_EVENTS_COURT_DOCUMENT_CREATED,
                "stub-data/public.progression.events.court-document-created-more-defendant-level.json", replacevalueMap);
        verifyPublicProgressionCourtDocumentAdded();

        final String responsePayload = queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("materialId", materialId1.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations[0].materialId", equalTo(materialId1.toString())),
                withJsonPath("$.caseOperations[0].transactionType", equalTo(MATERIAL_UPDATE.name())),
                withJsonPath("$.caseOperations[0].transactionStatus", equalTo(SENT.name())));

        final JsonObject responseObject =  new StringToJsonObjectConverter().convert(responsePayload);
        final String transactionRef = responseObject.getJsonArray("caseOperations").getValuesAs(JsonObject.class).stream()
                .map(obj -> obj.getString("transactionId"))
                .findFirst().orElse(null);

        final int responseCode = sendCommandToProcessTransactionStatus(transactionRef, dcsHelper.processTransactionStatusRequest(caseId, defendantId,"json/transactionStatusSuccessPayload.json"));
        assertThat(responseCode, is(HttpStatus.SC_ACCEPTED));

        queryHelper.queryTransactionDetailsAndAssertMatch(of(fromString(transactionRef)),
                withJsonPath("$.transactionsDetails", hasSize(1)),
                withJsonPath("$.transactionsDetails[0].transactionId", is(transactionRef)),
                withJsonPath("$.transactionsDetails[0].transactionType", is(MATERIAL_UPDATE.name())),
                withJsonPath("$.transactionsDetails[0].transactionStatus", equalTo(TransactionStatus.SUCCESS.name())));

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("defendantId", defendantId.toString(),"transactionType", "MATERIAL_UPDATE"),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(2)));

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("defendantId", defendantId2.toString(),"transactionType", "MATERIAL_UPDATE"),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(1)));
    }

    @Test
    void shouldSendMaterial_WhenCaseMaterialInitiated_WithMasterDefendantId() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        final UUID masterDefendantId = randomUUID();
        createSendMaterialFunctionalStubs(caseId.toString(), masterDefendantId.toString(), null, caseUrn);
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionServiceForRelatedCases(caseId, defendantId, masterDefendantId);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("defendantId", defendantId.toString(), "materialId", materialId2.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].materialId", equalTo(materialId2.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", equalTo(MATERIAL_UPDATE.name())));
    }

    @Test
    void shouldSendMaterial_WhenCaseMaterialInitiated_WithDefendantId() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        createSendMaterialFunctionalStubs(caseId.toString(), defendantId.toString(), null, caseUrn);
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId, defendantId);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("defendantId", defendantId.toString(), "materialId", materialId2.toString()),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].materialId", equalTo(materialId2.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", equalTo(MATERIAL_UPDATE.name())));
    }

    @Test
    void shouldUpdateDefendantRepresentationUpdatesWhenCaseUpdatesToDefenceOrganisationAssociated() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId);
        final String organisationId = randomUUID().toString();
        stubUserGoupsService(organisationId);
        stubDefenceService(defendantId.toString(), organisationId);
        stubDcsDefendantsDefenceUpdateCal(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record-for-organisation.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);
        sendDefencePublicEvent(DEFENCE_ASSOCIATED_EVENT,
                "stub-data/public.defence.defence-organisation-associated.json", caseId.toString(), defendantId.toString(), organisationId);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));

        verifyPublicEventConsumerForDefenceOrganisationAssociated();
        verifyDefendantStatusUpdated(caseId, LINKED, defendantId, LINKED, DCS_UPDATED);
    }
    @Test
    void shouldNotRetryOn404WhenDefendantRepresentationUpdatesToDcsRequested() {

        String urn = generateUrn();
        UUID defendantRef = randomUUID();
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        final UUID prosecutionCaseId = randomUUID();
        final UUID defId = randomUUID();
        final UUID caseReferralId = randomUUID();
        stubDcsCreateCallOnSuccess(prosecutionCaseId, defId, urn, defendantRef, caseReferralId);
        stubProgressionService(prosecutionCaseId, defId);
        final String organisationId = randomUUID().toString();
        stubUserGoupsService(organisationId);
        stubDefenceService(defId.toString(), organisationId);
        stubDcsDefendantsDefenceUpdateCalOn404Response(urn, defendantRef.toString(), CASE_NOT_FOUND.getValue(), CASE_NOT_FOUND.getValue(), randomUUID().toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(prosecutionCaseId, defId, urn, "json/stagingdcs.submit-dcs-case-record-for-organisation.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(prosecutionCaseId, LINKED, defId, LINKED);

        sendDefencePublicEvent(DEFENCE_ASSOCIATED_EVENT,
                "stub-data/public.defence.defence-organisation-associated-for-404.json", prosecutionCaseId.toString(), defId.toString(), organisationId);

        verifyPublicEventConsumerForDefenceOrganisationAssociated();
        verifyDefendantStatusUpdated(prosecutionCaseId, LINKED, defId, LINKED, DCS_NOT_UPDATED);
    }

    @Test
    void shouldRetryOn500_FailTransactionAfterRetryExhausted_WhenDefendantRepresentationUpdatesToDcsRequestedFailed() {

        String urn = generateUrn();
        UUID defendantRef = randomUUID();
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        final UUID prosecutionCaseId = randomUUID();
        final UUID defId = randomUUID();
        final UUID caseReferralId = randomUUID();
        stubDcsCreateCallOnSuccess(prosecutionCaseId, defId, urn, defendantRef, caseReferralId);
        stubProgressionService(prosecutionCaseId, defId);
        final String organisationId = randomUUID().toString();
        stubUserGoupsService(organisationId);
        stubDefenceService(defId.toString(), organisationId);
        stubDcsDefendantsDefenceUpdateCal(urn, defendantRef.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(prosecutionCaseId, defId, urn, "json/stagingdcs.submit-dcs-case-record-for-organisation.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        queryHelper.queryTransactionMetadataAndAssertMatch(prosecutionCaseId, null,
                withJsonPath("$.caseId", equalTo(prosecutionCaseId.toString())),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(2)),
                withJsonPath("$.defendants[0].defendantId", equalTo(defId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", anyOf(equalTo(LINK_DEFENDANT), equalTo(DEFENCE_REPRESENTATION))),
                withJsonPath("$.defendants[0].defendantOperations[1].transactionType", anyOf(equalTo(LINK_DEFENDANT), equalTo(DEFENCE_REPRESENTATION))),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionStatus", equalTo(SUCCESS)),
                withJsonPath("$.defendants[0].defendantOperations[1].transactionStatus", equalTo(SUCCESS))
        );

        stubDcsDefendantsDefenceUpdateCalOn500Response(urn, defendantRef.toString(), "RANDOM_ERROR", randomUUID().toString());
        sendDefencePublicEvent(DEFENCE_ASSOCIATED_EVENT,
                "stub-data/public.defence.defence-organisation-associated-for-404.json", prosecutionCaseId.toString(), defId.toString(), organisationId);

        verifyPublicEventConsumerForDefenceOrganisationAssociated();
        queryHelper.queryTransactionMetadataAndAssertMatch(prosecutionCaseId, Map.of("transactionType", TransactionType.DEFENCE_REPRESENTATION.toString(), "transactionStatus", TransactionStatus.RETRY.toString()),
                withJsonPath("$.caseId", equalTo(prosecutionCaseId.toString())),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantId", equalTo(defId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", equalTo(DEFENCE_REPRESENTATION)),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionStatus", equalTo(TransactionStatus.RETRY.toString())));

        queryHelper.queryTransactionMetadataAndAssertMatch(prosecutionCaseId, Map.of("transactionType", TransactionType.DEFENCE_REPRESENTATION.toString(), "transactionStatus", TransactionStatus.FAILED.toString()),
                withJsonPath("$.caseId", equalTo(prosecutionCaseId.toString())),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantId", equalTo(defId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", equalTo(DEFENCE_REPRESENTATION)),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionStatus", equalTo(TransactionStatus.FAILED.toString())));
    }

    @Test
    void shouldUnlinkCase_When404SplitAndMerge_DefendantRepresentationUpdatesToDcsRequested() {

        String urn = generateUrn();
        UUID defendantRef = randomUUID();
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        final UUID prosecutionCaseId = randomUUID();
        final UUID defId = randomUUID();
        final UUID caseReferralId = randomUUID();
        stubDcsCreateCallOnSuccess(prosecutionCaseId, defId, urn, defendantRef, caseReferralId);
        stubProgressionService(prosecutionCaseId, defId);
        final String organisationId = randomUUID().toString();
        stubUserGoupsService(organisationId);
        stubDefenceService(defId.toString(), organisationId);
        stubDcsDefendantsDefenceUpdateCalOn404Response(urn, defendantRef.toString(), CASE_HAS_SPLIT_OR_MERGED.getValue(), "case is merged", randomUUID().toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(prosecutionCaseId, defId, urn, "json/stagingdcs.submit-dcs-case-record-for-organisation.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(prosecutionCaseId, LINKED, defId, LINKED);

        sendDefencePublicEvent(DEFENCE_ASSOCIATED_EVENT,
                "stub-data/public.defence.defence-organisation-associated.json", prosecutionCaseId.toString(), defId.toString(), organisationId);

        verifyPublicEventConsumerForDefenceOrganisationAssociated();
        verifyCaseDefendantStatus(prosecutionCaseId, DCS_LINK_SEVERED, defId, NOT_LINKED);
    }

    @Test
    void shouldUpdateDefendantRepresentationUpdatesWhenCaseUpdatesToDefenceOrganisationDisAssociated() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId);
        final String organisationId = randomUUID().toString();
        stubDefenceService(defendantId.toString(), organisationId);
        stubUserGoupsService(organisationId);
        stubDcsDefendantsDefenceUpdateCal(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record-for-organisation.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);
        sendDefencePublicEvent(DEFENCE_DISASSOCIATED_EVENT,
                "stub-data/public.defence.defence-organisation-disassociated.json", caseId.toString(), defendantId.toString(), organisationId);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyPublicEventConsumerForDefenceOrganisationDisassociated();
    }

    @Test
    void shouldGet403ForbiddenWhenFeatureIsDisabled() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, FALSE));

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");
        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(FORBIDDEN.getStatusCode()));
    }

    @Test
    void shouldNotSendDefendantUpdatesWhenCaseDefendantChanged_ButNoUpdatesToPersonNameDoB() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId,defendantId);

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");
        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));

        sendPublicEvent(PUBLIC_PROGRESSION_CASE_DEFENDANT_CHANGED,
                "stub-data/public.progression.case-defendant-changed1.json", caseId.toString(), defendantId.toString());

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, null,
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", equalTo(LINK_DEFENDANT)),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionStatus", equalTo(SUCCESS)));
    }

    @Test
    void shoulSendMaterial_RelatedCases() {
        FeatureStubber.stubFeaturesFor(CONTEXT, Map.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        stubProgressionService(caseId, defendantId);
        stubDcsDefendantsUpdateCal(caseUrn, defendantReferral.toString());

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, LINKED, defendantId, LINKED);

        //This result depends on the stub data of document search
        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, Map.of("transactionType", "MATERIAL_UPDATE"),
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.caseOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(2)));


        final UUID caseId2 = randomUUID();
        final UUID defendantId2 = randomUUID();
        final String urn2 = generateUrn();
        final UUID defendantRef2 = randomUUID();
        final UUID caseReferral2 = randomUUID();

        //resetting stub data of document search, document type reference data
        createSendMaterialFunctionalStubs(caseId2.toString(), defendantId.toString(), null, urn2);
        getDocumentTypeAccessByIdStub(documentTypeId1.toString());

        stubDcsCreateCallOnSuccess(caseId2, defendantId2, urn2, defendantRef2, caseReferral2);
        stubProgressionServiceForRelatedCases(caseId2, defendantId2, defendantId);
        stubDcsDefendantsUpdateCal(urn2, defendantRef2.toString());

        final JsonObject payload2 = dcsHelper.createCaseinDcsRequest(caseId2, defendantId2, urn2, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode2 = sendCommandToAddRequest(payload2);
        assertThat(responseStatusCode2, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId2, LINKED, defendantId2, LINKED);

        //This result depends on the stub data of document search
        queryHelper.queryTransactionMetadataAndAssertMatch(caseId2, Map.of("transactionType", "MATERIAL_UPDATE"),
                withJsonPath("$.caseId", equalTo(caseId2.toString())),
                withJsonPath("$.caseOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(1)));
    }

    private List<UUID> getAllTransactionIdsFromStringResponse(final String tranMetaDataResponsePayload) {
        final List<UUID> tranUuidList = new ArrayList<>();
        final JsonObject response = new StringToJsonObjectConverter().convert(tranMetaDataResponsePayload);
        if (response.containsKey("defendants")) {
            final JsonArray defendantArray = response.getJsonArray("defendants");
            defendantArray.getValuesAs(JsonObject.class).stream()
                    .filter(defItem -> defItem.containsKey("defendantOperations"))
                    .filter(defItem -> !defItem.getJsonArray("defendantOperations").isEmpty())
                    .forEach(defItem -> {
                        defItem.getJsonArray("defendantOperations").getValuesAs(JsonObject.class)
                                .forEach(defOperation -> tranUuidList.add(fromString(defOperation.getString("transactionId"))));
                    });
        }
        if (response.containsKey("caseOperations")) {
            final JsonArray caseOperationsArray = response.getJsonArray("caseOperations");
            caseOperationsArray.getValuesAs(JsonObject.class)
                    .forEach(caseOperation -> tranUuidList.add(fromString(caseOperation.getString("transactionId"))));
        }
        return tranUuidList;
    }

    private void verifyCaseStatusLinked(final UUID caseId, final String defendantStatus, final UUID defendantId, final String caseStatus) {
        queryHelper.queryCaseDetailByCaseIdAndAssertMatch(caseId,
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantStatus", equalTo(defendantStatus)),
                withJsonPath("$.caseStatus", equalTo(caseStatus)));
    }
    private void verifyDefendantStatusUpdated(final UUID caseId, final String defendantStatus, final UUID defendantId, final String caseStatus, final String status) {
        queryHelper.queryCaseDetailByCaseIdAndAssertMatch(caseId,
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantStatus", equalTo(defendantStatus)),
                withJsonPath("$.defendants[0].latestDefendantOperations[0].status", equalTo(status)),
                withJsonPath("$.caseStatus", equalTo(caseStatus)));
    }

    private void verifyCaseDefendantStatus(final UUID caseId, final String defendantStatus, final UUID defendantId, final String caseStatus) {
        queryHelper.queryCaseDetailByCaseIdAndAssertMatch(caseId,
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantStatus", equalTo(defendantStatus)),
                withJsonPath("$.caseStatus", equalTo(caseStatus)));
    }

    private int sendCommandToAddRequest(final JsonObject payload) {
        try (Response response = SimpleRestClient.postRequestReturnResponse(ApiRestEndpoint.CREATE_CASE_IN_DCS_REQUEST, payload.toString())) {
            return response.getStatus();
        }
    }

    private int sendCommandToProcessTransactionStatus(final String transactionRefId, final JsonObject payload) {
        try (Response response = SimpleRestClient.postRequestReturnResponse(ApiRestEndpoint.PROCESS_TRANSACTION_STATUS_REQUEST, transactionRefId, payload.toString())) {
            return response.getStatus();
        }
    }
}

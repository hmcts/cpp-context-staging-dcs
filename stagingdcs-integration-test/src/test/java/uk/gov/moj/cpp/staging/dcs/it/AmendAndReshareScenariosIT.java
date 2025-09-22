package uk.gov.moj.cpp.staging.dcs.it;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.lang.Boolean.TRUE;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsApiAddMaterialCall;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsCreateCallOnSuccess;
import static uk.gov.moj.cpp.staging.dcs.stub.ReferenceDataServiceStub.prosecutorByProsecutionAuthorityStub;
import static uk.gov.moj.cpp.staging.dcs.util.Constants.CONTEXT;
import static uk.gov.moj.cpp.staging.dcs.util.Constants.FEATURE_STAGING_DCS;

import uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber;
import uk.gov.moj.cpp.staging.dcs.helper.DcsHelper;
import uk.gov.moj.cpp.staging.dcs.helper.QueryHelper;
import uk.gov.moj.cpp.staging.dcs.stub.ApiRestEndpoint;
import uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub;
import uk.gov.moj.cpp.staging.dcs.stub.SimpleRestClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.core.Response;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AmendAndReshareScenariosIT extends BaseIT {
    private final DcsHelper dcsHelper = new DcsHelper();
    private final QueryHelper queryHelper = new QueryHelper();

    @BeforeEach
    void eachSetUp() throws IOException, InterruptedException {
        setup();
        prosecutorByProsecutionAuthorityStub("CPS");
        createSendMaterialFunctionalStubs(caseId.toString(), defendantId.toString(), null, caseUrn);
        stubDcsApiAddMaterialCall(caseUrn);
    }

    @Test
    void shouldDeLinkCaseAndDefendant() {
        FeatureStubber.stubFeaturesFor(CONTEXT, ImmutableMap.of(FEATURE_STAGING_DCS, TRUE));

        // Link Case1 - Defendant1 - Offence1 - Linked
        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        DcsServiceStub.stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, "$.defendants[0].defendantId", defendantId, LINKED);

        /*
         *  Amend and Reshare
         * Case1 - Defendant1 - Offence1 remove QR => Delinked
         */
        final JsonObject amendPayload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record-remove-qr.json");

        final int responseStatusCode1 = sendCommandToAddRequest(amendPayload);
        assertThat(responseStatusCode1, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, "$.defendants[0].defendantId", defendantId, NOT_LINKED);
    }

    @Test
    void shouldLinkCaseAndDefendant_Multiple_Defendants() {
        FeatureStubber.stubFeaturesFor(CONTEXT, ImmutableMap.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);

        // Link Case1 - Defendant1 - Offence1
        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinked(caseId, "$.defendants[0].defendantId", defendantId, LINKED);

        /* Amend and Reshare
         * Case1 - Defendant1 - Offence1 removed and Offence2 added
         * Case1 - Defendant2 - Offence1 added
         */

        final UUID  defendantId2 = randomUUID();
        DcsServiceStub.stubDcsCreateCallOnSuccess_multipleDefendants(caseId,Arrays.asList(defendantId, defendantId2), caseUrn, defendantReferral, caseReferral);

        final JsonObject amendPayload = dcsHelper.createCaseWithMultipleDefendants(caseId, Arrays.asList(defendantId, defendantId2), caseUrn, "json/submit-dcs-case-record-multiple-defendants.json");
        final int responseStatusCode1 = sendCommandToAddRequest(amendPayload);
        assertThat(responseStatusCode1, is(ACCEPTED.getStatusCode()));
        verifyCaseStatusLinkedTwoDefendantSameStatus(caseId, LINKED, defendantId, defendantId2, LINKED);
    }

    private int sendCommandToAddRequest(final JsonObject payload) {
        try (Response response = SimpleRestClient.postRequestReturnResponse(ApiRestEndpoint.CREATE_CASE_IN_DCS_REQUEST, payload.toString())) {
            return response.getStatus();
        }
    }

    private void verifyCaseStatusLinked(final UUID caseId, final String jsonPath, final UUID defendantId, final String linked) {
        queryHelper.queryCaseDetailByCaseIdAndAssertMatch(caseId,
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath(jsonPath, equalTo(defendantId.toString())),
                withJsonPath("$.caseStatus", equalTo(linked)));
    }

    private void verifyCaseStatusLinkedTwoDefendantSameStatus(final UUID caseId, final String defendantStatus, final UUID defendantId1, final UUID defendantId2, final String caseStatus) {
        queryHelper.queryCaseDetailByCaseIdAndAssertMatch(caseId,
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantId", anyOf(equalTo(defendantId1.toString()),equalTo(defendantId2.toString()))),
                withJsonPath("$.defendants[1].defendantId", anyOf(equalTo(defendantId1.toString()),equalTo(defendantId2.toString()))),
                withJsonPath("$.defendants[0].defendantStatus", equalTo(defendantStatus)),
                withJsonPath("$.defendants[1].defendantStatus", equalTo(defendantStatus)),
                withJsonPath("$.caseStatus", equalTo(caseStatus)));
    }
}

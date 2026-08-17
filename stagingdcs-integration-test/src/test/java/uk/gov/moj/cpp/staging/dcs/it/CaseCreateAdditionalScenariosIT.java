package uk.gov.moj.cpp.staging.dcs.it;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.withoutJsonPath;
import static java.lang.Boolean.TRUE;
import static java.util.UUID.randomUUID;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.RETRY;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType.LINK_DEFENDANT;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsApiAddMaterialCall;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsCreateCallOnCaseNotCreated;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsCreateCallOnSuccess;
import static uk.gov.moj.cpp.staging.dcs.stub.ReferenceDataServiceStub.prosecutorByProsecutionAuthorityStub;
import static uk.gov.moj.cpp.staging.dcs.util.Constants.CONTEXT;
import static uk.gov.moj.cpp.staging.dcs.util.Constants.FEATURE_STAGING_DCS;

import uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber;
import uk.gov.moj.cpp.staging.dcs.helper.DcsHelper;
import uk.gov.moj.cpp.staging.dcs.helper.QueryHelper;
import uk.gov.moj.cpp.staging.dcs.stub.ApiRestEndpoint;
import uk.gov.moj.cpp.staging.dcs.stub.SimpleRestClient;

import java.io.IOException;
import java.util.UUID;

import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;

import com.google.common.collect.ImmutableMap;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CaseCreateAdditionalScenariosIT extends BaseIT {
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
    void shouldNotLinkCaseAndDefendant_WhenCaseNotCreatedAtDcs() {
        FeatureStubber.stubFeaturesFor(CONTEXT, ImmutableMap.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnCaseNotCreated(caseUrn, "CASE_NOT_CREATED", SC_NOT_FOUND);

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        queryHelper.queryCaseDetailByCaseIdAndAssertMatch(caseId,
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.caseStatus", equalTo(NOT_LINKED)));
    }
    @Test
    void shouldNotLinkSecondDefendantWhenDcsReturnsDefendantNotCreated() {
        final UUID defendantReferral = randomUUID();
        final UUID caseReferral = randomUUID();
        FeatureStubber.stubFeaturesFor(CONTEXT, ImmutableMap.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnSuccess(caseId, defendantId, caseUrn, defendantReferral, caseReferral);
        // Link Case1 - Defendant1 - Offence1
        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        queryHelper.queryCaseDetailByCaseIdAndAssertMatch(caseId,
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantId", equalTo(defendantId.toString())),
                withJsonPath("$.caseStatus", equalTo(LINKED)));

        // Case1 - Defendant2 - Offence1 returns DEFENDANT_NOT_CREATED

        stubDcsCreateCallOnCaseNotCreated(caseUrn, "DEFENDANT_NOT_CREATED", SC_NOT_FOUND);
        final UUID  defendantId2 = randomUUID();
        final JsonObject jsonPayload = dcsHelper.createCaseinDcsRequest(caseId, defendantId2,"json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode1 = sendCommandToAddRequest(jsonPayload);
        assertThat(responseStatusCode1, is(ACCEPTED.getStatusCode()));
        queryHelper.queryCaseDetailByCaseIdAndAssertMatch(caseId,
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantId", anyOf(equalTo(defendantId.toString()), equalTo(defendantId2.toString()))),
                withJsonPath("$.defendants[0].defendantStatus", anyOf(equalTo(LINKED), equalTo(NOT_LINKED))),
                withJsonPath("$.defendants[1].defendantId", anyOf(equalTo(defendantId.toString()), equalTo(defendantId2.toString()))),
                withJsonPath("$.defendants[1].defendantStatus", anyOf(equalTo(LINKED), equalTo(NOT_LINKED))),
                withJsonPath("$.caseStatus", equalTo(LINKED)));
    }

    private int sendCommandToAddRequest(final JsonObject payload) {
        final Response response = SimpleRestClient.postRequestReturnResponse(ApiRestEndpoint.CREATE_CASE_IN_DCS_REQUEST, payload.toString());
        return response.getStatus();
    }

    @Test
    void shouldNotLinkCaseAndDefendant_RetryOnError500() {
        FeatureStubber.stubFeaturesFor(CONTEXT, ImmutableMap.of(FEATURE_STAGING_DCS, TRUE));
        stubDcsCreateCallOnCaseNotCreated(caseUrn, "CASE_NOT_CREATED", SC_INTERNAL_SERVER_ERROR);

        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, defendantId, caseUrn, "json/stagingdcs.submit-dcs-case-record.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
        queryHelper.queryCaseDetailByCaseIdAndAssertMatch(caseId,
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants",hasSize(0)),
                withoutJsonPath("$.caseStatus"));

        queryHelper.queryTransactionMetadataAndAssertMatch(caseId, null,
                withJsonPath("$.caseId", equalTo(caseId.toString())),
                withJsonPath("$.defendants[0].defendantOperations", hasSize(1)),
                withJsonPath("$.defendants[0].defendantId", CoreMatchers.equalTo(defendantId.toString())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionType", equalTo(LINK_DEFENDANT.name())),
                withJsonPath("$.defendants[0].defendantOperations[0].transactionStatus", equalTo(RETRY.name())));
    }

}

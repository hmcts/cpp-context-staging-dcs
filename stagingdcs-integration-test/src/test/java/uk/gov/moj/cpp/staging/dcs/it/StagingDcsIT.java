package uk.gov.moj.cpp.staging.dcs.it;

import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.staging.dcs.stub.SimpleRestClient.postRequestReturnResponse;

import uk.gov.moj.cpp.staging.dcs.helper.DcsHelper;
import uk.gov.moj.cpp.staging.dcs.stub.ApiRestEndpoint;
import uk.gov.moj.cpp.staging.dcs.stub.SimpleRestClient;

import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StagingDcsIT extends BaseIT {
    private UUID caseId;
    private final DcsHelper dcsHelper = new DcsHelper();

    @BeforeEach
    void setup() {
        caseId = randomUUID();
    }

    //To be replaced/updated once the command api is implemented
    @Test
    void shouldInvokeCommandApi() {
        final JsonObject payload = dcsHelper.createCaseinDcsRequest(caseId, "json/stagingdcs.create-case-defendant-file-in-dcs.json");

        final int responseStatusCode = sendCommandToAddRequest(payload);
        assertThat(responseStatusCode, is(ACCEPTED.getStatusCode()));
    }

    private int sendCommandToAddRequest(final JsonObject payload) {
        final Response response = SimpleRestClient.postRequestReturnResponse(ApiRestEndpoint.CREATE_CASE_IN_DCS_REQUEST, payload.toString());
        return response.getStatus();
    }
}

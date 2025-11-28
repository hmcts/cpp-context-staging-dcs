package uk.gov.moj.cpp.staging.dcs.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static javax.json.Json.createObjectBuilder;
import static org.apache.http.HttpStatus.SC_ACCEPTED;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.ERROR_CODE;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.TRANSACTION_REF;
import static uk.gov.moj.cpp.staging.dcs.util.FileUtil.getPayload;

import java.util.List;
import java.util.UUID;

import org.apache.http.HttpStatus;

public class DcsServiceStub {

    public static final String SERVER_ERROR = "SERVER_ERROR";

    public static void stubDcsCreateCallOnSuccess(final UUID caseId, final UUID defendantId, final String caseUrn, final UUID defendantReferral, final UUID caseReferral) {
        String body = getPayload("stub-data/link-case-defendant-response.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DEFENDANT_REFERRAL", defendantReferral.toString())
                .replaceAll("CASE_REFERRAL", caseReferral.toString());

        final String url = String.format("/dcs-outbound/v1/mock/case/%s/defendants", caseUrn);

        stubFor(post(urlPathEqualTo(url))
                .willReturn(aResponse().withStatus(SC_CREATED).withBody(body)));
    }

    public static void stubDcsCreateCall_On500Error(final String caseUrn) {
        final String url = String.format("/dcs-outbound/v1/mock/case/%s/defendants", caseUrn);

        stubFor(post(urlPathEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(SC_INTERNAL_SERVER_ERROR)
                        .withBody(createObjectBuilder()
                                .add(ERROR_MESSAGE, SERVER_ERROR)
                                .add(TRANSACTION_REF, SERVER_ERROR)
                                .build().toString()
                        )));
    }

    public static void stubDcsCreateCallOnCaseNotCreated(final String caseUrn, final String errorCode, final int error) {
        String body = getPayload("stub-data/link-case-defendant-response-when-case-not-created.json")
                .replaceAll("ERROR_CODE", errorCode);

        final String url = String.format("/dcs-outbound/v1/mock/case/%s/defendants", caseUrn);

        stubFor(post(urlPathEqualTo(url))
                .willReturn(aResponse().withStatus(error).withBody(body)));
    }

    public static void stubDcsCreateCallOnSuccess_multipleDefendants(final UUID caseId, final List<UUID> defendantIds, final String caseUrn, final UUID defendantReferral, final UUID caseReferral) {
        String body = getPayload("stub-data/link-case-defendant-response-multiple-defendants.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID1", defendantIds.get(0).toString())
                .replaceAll("DEFENDANT_ID2", defendantIds.get(1).toString())
                .replaceAll("DEFENDANT_REFERRAL", defendantReferral.toString())
                .replaceAll("CASE_REFERRAL", caseReferral.toString());

        final String url = String.format("/dcs-outbound/v1/mock/case/%s/defendants", caseUrn);

        stubFor(post(urlPathEqualTo(url))
                .willReturn(aResponse().withStatus(SC_CREATED).withBody(body)));
    }

    public static void stubDcsDefendantsUpdateCal(final String caseUrn, final String defendantReferral) {
        final String url = String.format("/dcs-outbound/v1/mock/case/%s/defendant/%s", caseUrn, defendantReferral);

        stubFor(post(urlPathEqualTo(url))
                .willReturn(aResponse().withStatus(SC_NO_CONTENT)));
    }

    public static void stubDcsDefendantsUpdateCal_500Error(final String caseUrn, final String defendantReferral) {
        final String url = String.format("/dcs-outbound/v1/mock/case/%s/defendant/%s", caseUrn, defendantReferral);

        stubFor(post(urlPathEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(SC_INTERNAL_SERVER_ERROR)
                        .withBody(createObjectBuilder()
                                .add(ERROR_MESSAGE, SERVER_ERROR)
                                .add(TRANSACTION_REF, SERVER_ERROR)
                                .build().toString()
                        )));
    }

    public static void stubDcsDefendantsUpdateCall_WithErrorCode(final String caseUrn, final String defendantReferral, final String errorCode, final String errorMessage, final String transactionRefId) {
        final String url = String.format("/dcs-outbound/v1/mock/case/%s/defendant/%s", caseUrn, defendantReferral);

        stubFor(post(urlPathEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(SC_NOT_FOUND)
                        .withBody(createObjectBuilder()
                                .add(ERROR_CODE, errorCode)
                                .add(ERROR_MESSAGE, errorMessage)
                                .add(TRANSACTION_REF, transactionRefId)
                                .build().toString()
                        )
                ));
    }

    public static void stubDcsErrorWhenDefendantsUpdateCal(final String caseUrn, final String defendantReferral, final String errorCode, final String errorMessage, final String transactionRefId) {
        final String url = String.format("/dcs-outbound/v1/mock/case/%s/defendant/%s", caseUrn, defendantReferral);

        stubFor(post(urlPathEqualTo(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND).withBody(createObjectBuilder()
                        .add(ERROR_CODE, errorCode)
                        .add(ERROR_MESSAGE, errorMessage)
                        .add(TRANSACTION_REF, transactionRefId)
                        .build().toString())));
    }

    public static void stubDcsDefendantsDefenceUpdateCal(final String caseUrn, final String defendantReferral) {
        final String url = String.format("/dcs-outbound/v1/mock/case/%s/defendant/%s/defenceRepresentation", caseUrn, defendantReferral);
        stubFor(post(urlPathEqualTo(url))
                .willReturn(aResponse().withStatus(SC_NO_CONTENT)));
    }

    public static void stubDcsDefendantsDefenceUpdateCalOn404Response(final String caseUrn, final String defendantReferral, final String errorCode, final String errorMessage, final String transactionRefId) {
        final String url = String.format("/dcs-outbound/v1/mock/case/%s/defendant/%s/defenceRepresentation", caseUrn, defendantReferral);

        stubFor(post(urlPathEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(SC_NOT_FOUND)
                        .withBody(createObjectBuilder()
                                .add(ERROR_CODE, errorCode)
                                .add(ERROR_MESSAGE, errorMessage)
                                .add(TRANSACTION_REF, transactionRefId)
                                .build().toString()
                        )));
    }

    public static void stubDcsDefendantsDefenceUpdateCalOn500Response(final String caseUrn, final String defendantReferral, final String errorMessage, final String transactionRefId) {
        final String url = String.format("/dcs-outbound/v1/mock/case/%s/defendant/%s/defenceRepresentation", caseUrn, defendantReferral);

        stubFor(post(urlPathEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(SC_INTERNAL_SERVER_ERROR)
                        .withBody(createObjectBuilder()
                                .add(ERROR_MESSAGE, errorMessage)
                                .add(TRANSACTION_REF, transactionRefId)
                                .build().toString()
                        )));
    }

    public static void stubDcsApiAddMaterialCall(final String caseUrn) {
        final String url = String.format("/dcs-outbound/v1/mock/case/%s/material", caseUrn);
        stubFor(post(urlPathEqualTo(url))
                .willReturn(aResponse().withStatus(SC_ACCEPTED)));
    }

    public static void stubDcsApiAddMaterialCall_500Error(final String caseUrn) {
        final String url = String.format("/dcs-outbound/v1/mock/case/%s/material", caseUrn);
        stubFor(post(urlPathEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(SC_INTERNAL_SERVER_ERROR)
                        .withBody(createObjectBuilder()
                                .add(ERROR_MESSAGE, SERVER_ERROR)
                                .add(TRANSACTION_REF, SERVER_ERROR)
                                .build().toString()
                        )));
    }

}

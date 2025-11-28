package uk.gov.moj.cpp.staging.dcs.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.moj.cpp.staging.dcs.util.FileUtil.getPayload;
import static uk.gov.moj.cpp.staging.dcs.util.FileUtil.getPayloadWithReplacedValues;
import static uk.gov.moj.cpp.staging.dcs.util.FileUtil.resourceToString;

import java.util.Map;
import java.util.UUID;

public class ProgressionServiceStub {

    private static final String PROGRESSION_QUERY_PROSECUTION_CASE = "/progression-service/query/api/rest/progression/prosecutioncases/";
    private static final String PROGRESSION_QUERY_ALL_COURT_DOCUMENTS = "/progression-service/query/api/rest/progression/courtdocumentsearch";
    private static final String PROGRESSION_QUERY_ALL_COURT_DOCUMENTS_MEDIA_TYPE = "application/vnd.progression.query.courtdocuments-all+json";
    private static final String PROGRESSION_QUERY_PROSECUTION_CASE_MEDIA_TYPE = "application/vnd.progression.query.prosecutioncase-v2+json";

    public static void stubProgressionService(UUID caseId) {
        stubFor(get(urlPathEqualTo(PROGRESSION_QUERY_PROSECUTION_CASE + caseId.toString()))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", UUID.randomUUID().toString())
                        .withHeader("Content-Type", PROGRESSION_QUERY_PROSECUTION_CASE_MEDIA_TYPE)
                        .withBody(resourceToString("stub-data/progression.query.prosecutioncase.json"))));
    }

    public static void stubProgressionServiceAllCourtDocuments(String caseId, Map<String,String> replaceValuesString) {
        stubFor(get(urlPathEqualTo(PROGRESSION_QUERY_ALL_COURT_DOCUMENTS))
                .withQueryParam("caseId", equalTo(caseId))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", UUID.randomUUID().toString())
                        .withHeader("Content-Type", PROGRESSION_QUERY_ALL_COURT_DOCUMENTS_MEDIA_TYPE)
                        .withBody(getPayloadWithReplacedValues("stub-data/all-progression-courtdocument-search.json", replaceValuesString))));
    }

    public static void stubProgressionService(final UUID caseId, final UUID defendantId) {
        String body = getPayload("stub-data/progression.query.prosecutioncase1.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString());

        stubFor(get(urlPathEqualTo(PROGRESSION_QUERY_PROSECUTION_CASE + caseId.toString()))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", UUID.randomUUID().toString())
                        .withHeader("Content-Type", PROGRESSION_QUERY_PROSECUTION_CASE_MEDIA_TYPE)
                        .withBody(body)));
    }

    public static void stubProgressionServiceWith2Defendant(final UUID caseId, final UUID defendantId1, final UUID defendantId2) {
        String body = getPayload("stub-data/progression.query.prosecutioncase-with-two-defendant.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID_1", defendantId1.toString())
                .replaceAll("DEFENDANT_ID_2", defendantId2.toString());

        stubFor(get(urlPathEqualTo(PROGRESSION_QUERY_PROSECUTION_CASE + caseId.toString()))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", UUID.randomUUID().toString())
                        .withHeader("Content-Type", PROGRESSION_QUERY_PROSECUTION_CASE_MEDIA_TYPE)
                        .withBody(body)));
    }

    public static void stubProgressionServiceForRelatedCases(final UUID caseId, final UUID defendantId, final UUID masterDefendantId) {
        String body = getPayload("stub-data/progression.query.prosecutioncase2.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("MASTER_DEFENDANT_ID", masterDefendantId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString());

        stubFor(get(urlPathEqualTo(PROGRESSION_QUERY_PROSECUTION_CASE + caseId.toString()))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", UUID.randomUUID().toString())
                        .withHeader("Content-Type", PROGRESSION_QUERY_PROSECUTION_CASE_MEDIA_TYPE)
                        .withBody(body)));
    }


}

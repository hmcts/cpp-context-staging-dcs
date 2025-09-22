package uk.gov.moj.cpp.staging.dcs.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.justice.services.common.http.HeaderConstants.ID;
import static uk.gov.moj.cpp.staging.dcs.util.FileUtil.resourceToString;

import org.apache.http.HttpHeaders;

public class DefenceServiceStub {

    private static final String DEFENCE_QUERY_ASSOCIATED_ORGANISATION = "/defence-service/query/api/rest/defence/defendants/%s/associatedOrganisation";
    private static final String DEFENCE_QUERY_ASSOCIATED_ORGANISATION_MEDIA_TYPE = "application/vnd.defence.query.associated-organisation+json";

    public static void stubDefenceService(String defendantId, final String organisationId) {
        stubFor(get(urlPathEqualTo(format(DEFENCE_QUERY_ASSOCIATED_ORGANISATION, defendantId)))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, DEFENCE_QUERY_ASSOCIATED_ORGANISATION_MEDIA_TYPE)
                        .withBody(resourceToString("stub-data/associated-organisation.json").replaceAll("ORGANISATION_ID", organisationId))));
    }


}

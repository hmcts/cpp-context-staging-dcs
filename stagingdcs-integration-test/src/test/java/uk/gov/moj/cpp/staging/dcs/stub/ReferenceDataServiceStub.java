package uk.gov.moj.cpp.staging.dcs.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.time.format.DateTimeFormatter.ISO_DATE;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.justice.services.common.http.HeaderConstants.ID;
import static uk.gov.moj.cpp.staging.dcs.util.FileUtil.getJsonResponse;
import static uk.gov.moj.cpp.staging.dcs.util.FileUtil.getPayload;
import static uk.gov.moj.cpp.staging.dcs.util.FileUtil.getPayloadWithReplacedValues;

import java.time.LocalDate;
import java.util.Map;

public class ReferenceDataServiceStub {

    private static final String REFERENCE_DATA_QUERY_PROSECUTORS = "/referencedata-service/query/api/rest/referencedata/prosecutors";

    private static final String REFERENCE_DATA_QUERY_ALL_DOCUMENT_TYPE_ACCESS = "/referencedata-service/query/api/rest/referencedata/documents-type-access/";

    private static final String REFERENCE_DATA_QUERY_ALL_DOCUMENT_TYPE_ACCESS_MEDIA_TYPE = "application/vnd.referencedata.get-all-document-type-access+json";

    private static final String REFERENCE_DATA_QUERY_DOCUMENT_TYPE_ACCESS_BY_ID = "/referencedata-service/query/api/rest/referencedata/document-type-access/%s";
    private static final String REFERENCE_DATA_QUERY_DOCUMENT_TYPE_ACCESS_BY_ID_MEDIA_TYPE = "application/vnd.referencedata.query.document-type-access+json";
    public static void prosecutorByProsecutionAuthorityStub(final String prosecutionAuthority) {

        stubFor(get(urlPathEqualTo(REFERENCE_DATA_QUERY_PROSECUTORS))
                .withQueryParam("prosecutorCode", equalTo(prosecutionAuthority))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(getJsonResponse("stub-data/referencedata.query.prosecutor-by-prosecution-authority.json")
                                .replaceAll("PROSECUTOR_AUTHORITY_CODE", prosecutionAuthority)
                        )));
    }

    public static void getAllDocumentTypeAccessStub(final Map<String,String> replaceValuesMap) {

        stubFor(get(urlPathEqualTo(REFERENCE_DATA_QUERY_ALL_DOCUMENT_TYPE_ACCESS.concat(LocalDate.now().format(ISO_DATE))))
                .withHeader(ACCEPT, equalTo(REFERENCE_DATA_QUERY_ALL_DOCUMENT_TYPE_ACCESS_MEDIA_TYPE))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(CONTENT_TYPE, REFERENCE_DATA_QUERY_ALL_DOCUMENT_TYPE_ACCESS_MEDIA_TYPE)
                        .withBody(getPayloadWithReplacedValues("stub-data/reference-data-all-document-type-access.json", replaceValuesMap))));
    }

    public static void getDocumentTypeAccessByIdStub(final String documentTypeAccessId) {

        stubFor(get(urlPathEqualTo(String.format(REFERENCE_DATA_QUERY_DOCUMENT_TYPE_ACCESS_BY_ID, documentTypeAccessId)))
                .withHeader(ACCEPT, equalTo(REFERENCE_DATA_QUERY_DOCUMENT_TYPE_ACCESS_BY_ID_MEDIA_TYPE))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(CONTENT_TYPE, REFERENCE_DATA_QUERY_ALL_DOCUMENT_TYPE_ACCESS_MEDIA_TYPE)
                        .withBody(getPayload("stub-data/reference-data-document-type-access-by-id.json")
                                .replaceAll("DOCUMENT_TYPE_ACCESS_ID", documentTypeAccessId))));
    }
}
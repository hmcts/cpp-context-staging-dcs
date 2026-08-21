package uk.gov.moj.cpp.staging.dcs.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpHeaders.LOCATION;
import static org.apache.http.HttpStatus.SC_OK;

import java.util.Map;

import jakarta.json.JsonObjectBuilder;

public class MaterialServiceStub {

    private static final String MATERIAL_QUERY_MATERIAL_BY_ID = "/material-service/query/api/rest/material/material/.*?";
    private static final String MATERIAL_QUERY_DOWNLOADABLE_MATERIAL = "/material-service/query/api/rest/material/materials";
    private static final String MATERIAL_QUERY_DOWNLOADABLE_MATERIAL_MEDIA_TYPE = "application/vnd.material.query.is-downloadable-materials+json";

    private static final String MATERIAL_IDS = "materialIds";

    public static void stubMaterialServiceDownloadableMaterials(Map<String, Boolean> downloadableMaterials) {
        stubFor(get(urlPathMatching(MATERIAL_QUERY_DOWNLOADABLE_MATERIAL))
                .withQueryParam(MATERIAL_IDS, matching(".*?"))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader(ACCEPT, MATERIAL_QUERY_DOWNLOADABLE_MATERIAL_MEDIA_TYPE)
                        .withBody(downloadableMaterialResponse(downloadableMaterials))));
    }

    /* Observe below method header and body value.
    * Header value and body is public url of file. If this url is no longer accessible then change it
    * with another public url document from uk. This url is simply mocking a sasUrl of material.
    * */
    public static void stubQueryMaterialById() {
        stubFor(get(urlPathMatching(MATERIAL_QUERY_MATERIAL_BY_ID))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader(LOCATION, "https://assets.publishing.service.gov.uk/media/6756fee87ec19a3516f79a50/CJS_Courts_BC_OU_Codes_v37.ods")
                        .withBody("https://assets.publishing.service.gov.uk/media/6756fee87ec19a3516f79a50/CJS_Courts_BC_OU_Codes_v37.ods")));
    }

    private static String downloadableMaterialResponse(final Map<String, Boolean> downloadableMaterials) {
        JsonObjectBuilder objectBuilder = createObjectBuilder();
        downloadableMaterials.forEach(objectBuilder::add);
        return createObjectBuilder()
                .add("materials", objectBuilder.build())
                .build()
                .toString();
    }
}

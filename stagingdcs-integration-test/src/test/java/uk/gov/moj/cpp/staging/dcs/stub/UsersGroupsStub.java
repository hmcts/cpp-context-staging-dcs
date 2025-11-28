package uk.gov.moj.cpp.staging.dcs.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.moj.cpp.staging.dcs.stub.SimpleFileClient.getFile;
import static uk.gov.moj.cpp.staging.dcs.util.FileUtil.resourceToString;

import java.io.IOException;
import java.util.UUID;

public class UsersGroupsStub {

    public static final String BASE_QUERY = "/usersgroups-service/query/api/rest/usersgroups";
    private static final String USERSGROUPS_ALL_USERS_QUERY_URL = BASE_QUERY + "/users/.*";
    private static final String USERSGROUPS_GROUPS_WITH_ORGANISATION_QUERY_URL = BASE_QUERY + "/groups/organisation";
    private static final String USERGROUPS_QUERY_ORGANISATION_DETAILS = "/usersgroups-service/query/api/rest/usersgroups/organisations/";
    private static final String USERGROUPS_QUERY_ORGANISATION_DETAILS_MEDIA_TYPE = "application/vnd.usersgroups.get-organisation-details+json";

    public static void stubUsersAndGroups() throws IOException {
        stubFor(get(urlPathMatching(USERSGROUPS_ALL_USERS_QUERY_URL))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", UUID.randomUUID().toString())
                        .withHeader("Content-Type", "application/json")
                        .withBody(getFile("stub-data/usersgroups.user-groups.json").asString())));

    }

    public static void stubQueryGroupsWithOrganisation() throws IOException {
        stubFor(get(urlPathMatching(USERSGROUPS_GROUPS_WITH_ORGANISATION_QUERY_URL))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", UUID.randomUUID().toString())
                        .withHeader("Content-Type", "application/json")
                        .withBody(getFile("stub-data/usersgroups.get-groups-with-organisation.json").asString())));
    }

    public static void stubUserGoupsService(String organisationId) {
        stubFor(get(urlPathEqualTo(USERGROUPS_QUERY_ORGANISATION_DETAILS + organisationId))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", UUID.randomUUID().toString())
                        .withHeader("Content-Type", USERGROUPS_QUERY_ORGANISATION_DETAILS_MEDIA_TYPE)
                        .withBody(resourceToString("stub-data/usergroups.organisation-details.json"))));
    }
}

package uk.gov.moj.cpp.staging.dcs.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.moj.cpp.staging.dcs.stub.SimpleFileClient.getFile;

import java.io.IOException;
import java.util.UUID;

public class UsersGroupsStub {

    public static final String BASE_QUERY = "/usersgroups-service/query/api/rest/usersgroups";
    private static final String USERSGROUPS_ALL_USERS_QUERY_URL = BASE_QUERY + "/users/.*";
    private static final String USERSGROUPS_GROUPS_WITH_ORGANISATION_QUERY_URL = BASE_QUERY + "/groups/organisation";

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
}

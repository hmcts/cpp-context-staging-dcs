package uk.gov.moj.cpp.staging.dcs.it;

import static com.github.tomakehurst.wiremock.client.WireMock.reset;

import uk.gov.moj.cpp.staging.dcs.stub.IdMapperStub;
import uk.gov.moj.cpp.staging.dcs.stub.UsersGroupsStub;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeAll;

public class BaseIT {

    @BeforeAll
    public static void setupOnce() throws Throwable {
        WireMock.configureFor(System.getProperty("INTEGRATION_HOST_KEY", "localhost"), 8080);
        reset();
        IdMapperStub.setUp();
        UsersGroupsStub.stubUsersAndGroups();
    }
}

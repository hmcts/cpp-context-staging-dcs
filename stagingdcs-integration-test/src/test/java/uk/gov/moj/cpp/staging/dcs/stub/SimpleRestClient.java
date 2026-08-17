package uk.gov.moj.cpp.staging.dcs.stub;

import static uk.gov.justice.services.test.utils.core.http.BaseUriProvider.getBaseUri;

import uk.gov.justice.services.common.http.HeaderConstants;
import uk.gov.justice.services.test.utils.core.rest.RestClient;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

public class SimpleRestClient {

    public static final String SYSTEM_USER_ID = "45899b95-3506-431d-b083-5c69256862c6";

    public static Response postRequestReturnResponse(final ApiRestEndpoint apiRestEndpoint, final String resourceId, final String payload) {
        final String url = getBaseUri() + apiRestEndpoint.getUri() + "/" + resourceId;
        return new RestClient().postCommand(url, apiRestEndpoint.getMediaType(), payload, newHeadersMap());
    }

    public static Response postRequestReturnResponse(final ApiRestEndpoint apiRestEndpoint, final String payload) {
        final String url = getBaseUri() + apiRestEndpoint.getUri();
        return new RestClient().postCommand(url, apiRestEndpoint.getMediaType(), payload, newHeadersMap());
    }

    private static MultivaluedMap<String, Object> newHeadersMap() {
        final MultivaluedMap<String, Object> map = new MultivaluedHashMap<>();
        map.add(HeaderConstants.USER_ID, SYSTEM_USER_ID);
        return map;
    }
}

package uk.gov.moj.cpp.staging.dcs.stub;

public enum ApiRestEndpoint {
    CREATE_CASE_IN_DCS_REQUEST(
            "/stagingdcs-command-api/command/api/rest/create-case",
            "application/vnd.stagingdcs.create-case-defendant-file-in-dcs+json");

    private final String uri;
    private final String mediaType;

    ApiRestEndpoint(final String uri, final String mediaType) {
        this.uri = uri;
        this.mediaType = mediaType;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getUri() {
        return uri;
    }
}

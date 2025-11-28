package uk.gov.moj.cpp.staging.dcs.stub;

public enum ApiRestEndpoint {
    CREATE_CASE_IN_DCS_REQUEST(
            "/stagingdcs-command-api/command/api/rest/stagingdcs/create-case",
            "application/vnd.stagingdcs.submit-dcs-case-record+json"),

    PROCESS_TRANSACTION_STATUS_REQUEST(
            "/stagingdcs-command-api/command/api/rest/stagingdcs/transaction",
                    "application/vnd.stagingdcs.process-dcs-transaction-status+json");

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

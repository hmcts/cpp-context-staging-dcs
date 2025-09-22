package uk.gov.moj.cpp.staging.dcs.event.service.exception;

public class CloudException extends RuntimeException{

    public CloudException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public CloudException(final String message) {
        super(message);
    }
}

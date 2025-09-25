package uk.gov.moj.cpp.staging.dcs.domain.common;

public enum TransactionStatus {
    SENT("SENT"),

    SUCCESS("SUCCESS"),

    FAILED("FAILED"),

    RETRY("RETRY"),

    PENDING("PENDING");

    private final String value;

    TransactionStatus(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}

package uk.gov.moj.cpp.staging.dcs.domain.common;

public enum DcsDefendantStatus {
    PENDING("PENDING"),

    LINKED("LINKED"),

    FAILED("FAILED"),

    AWAITING("AWAITING"),

    UNLINKED("UNLINKED");

    private final String value;

    DcsDefendantStatus(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}

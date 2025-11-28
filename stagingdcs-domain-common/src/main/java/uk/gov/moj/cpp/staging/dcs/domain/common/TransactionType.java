package uk.gov.moj.cpp.staging.dcs.domain.common;

public enum TransactionType {
    LINK_DEFENDANT("LINK_DEFENDANT"),

    DEFENDANT_UPDATE("DEFENDANT_UPDATE"),

    DEFENCE_REPRESENTATION("DEFENCE_REPRESENTATION"),

    MATERIAL_UPDATE("MATERIAL_UPDATE");

    private final String value;

    TransactionType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}

package uk.gov.moj.cpp.staging.dcs.domain.common;

import java.time.format.DateTimeFormatter;

public final class Constants {
    private Constants() {}

    public static final String FEATURE_STAGING_DCS = "StagingDcs";
    public static final String CASE_URN = "caseUrn";
    public static final String CREATE_PAYLOAD = "createPayload";
    public static final String TRANSACTION_REF = "transactionRef";

    public static final String CASE_ID = "caseId";
    public static final String DEFENDANT_ID = "defendantId";
    public static final String ACTION = "action";
    public static final String METADATA = "metadata";
    public static final String ORGANISATION_ID = "organisationId";
    public static final String PROSECUTION_AUTHORITY = "prosecutionAuthority";
    public static final String COURT_CENTRE = "courtCentre";
    public static final String HEARING_DATE = "hearingDate";
    public static final String DEFENDANTS = "defendants";
    public static final String HEARINGS = "hearings";
    public static final String ID = "id";
    public static final String BAIL_STATUS = "bailStatus";
    public static final String INTERPRETER_LANGUAGE = "interpreterLanguage";
    public static final String INTERPRETER_INFORMATION = "interpreterInformation";
    public static final String DEFENDANT_ORGANISATION = "defendantOrganisation";
    public static final String DEFENDANT_PERSON = "defendantPerson";
    public static final String FORENAME = "forename";
    public static final String MIDDLENAME = "middleName";
    public static final String SURNAME = "surname";
    public static final String DOB = "dateOfBirth";
    public static final String NAME = "name";
    public static final String CASE_REFERRAL = "caseReferral";
    public static final String MATERIAL_ID = "materialId";
    public static final String MATERIAL_URL = "materialUrl";
    public static final String DOCUMENT_NAME = "documentName";
    public static final String DOCUMENT_DATE = "documentDate";
    public static final String UPLOADED_BY_USER_KEY = "uploadedByUser";
    public static final String UPLOADED_BY_USER_VALUE = "Common Platform";
    public static final String DOCUMENT_SECTION = "documentSection";
    public static final String DEFENDANT_REFERRAL = "defendantReferral";
    public static final String ERROR_MESSAGE = "errorMessage";
    public static final String ERROR_CODE = "errorCode";
    public static final DateTimeFormatter SIMPLE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter ZONE_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");


}

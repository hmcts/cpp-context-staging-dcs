package uk.gov.moj.cpp.staging.dcs.event.service;

import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.getString;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.BAIL_STATUS;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_REFERRAL;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.COURT_CENTRE;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DEFENDANTS;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DEFENDANT_ORGANISATION;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DEFENDANT_PERSON;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DOB;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.FORENAME;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.HEARINGS;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.HEARING_DATE;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.INTERPRETER_INFORMATION;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.INTERPRETER_LANGUAGE;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.MIDDLENAME;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.NAME;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.PROSECUTION_AUTHORITY;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.SURNAME;

import uk.gov.hmcts.dcs.openapi.model.Defendant;
import uk.gov.hmcts.dcs.openapi.model.DefendantOrganisation;
import uk.gov.hmcts.dcs.openapi.model.DefendantPerson;
import uk.gov.hmcts.dcs.openapi.model.Hearing;
import uk.gov.hmcts.dcs.openapi.model.LinkCaseAndDefendantRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonValue;

@SuppressWarnings("squid:S2637")
public class LinkCaseAndDefendantRequestConverter {

    public LinkCaseAndDefendantRequest convert(final JsonObject payload, final String transactionRef){
        final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest = new LinkCaseAndDefendantRequest();

        linkCaseAndDefendantRequest.setTransactionRef(transactionRef);
        linkCaseAndDefendantRequest.setCaseId(payload.getString(CASE_ID));
        linkCaseAndDefendantRequest.setProsecutedBy(LinkCaseAndDefendantRequest.ProsecutedByEnum.valueOf(payload.getString(PROSECUTION_AUTHORITY)));

        if(nonNull(getValue(payload,CASE_REFERRAL))) {
            linkCaseAndDefendantRequest.setCaseReferral(payload.getString(CASE_REFERRAL));
        }

        final JsonArray defendantsArray = payload.getJsonArray(DEFENDANTS);
        final List<Defendant> defendants = getDefendants(defendantsArray);

        linkCaseAndDefendantRequest.setDefendants(defendants);
        return linkCaseAndDefendantRequest;
    }

    private static List<Defendant> getDefendants(final JsonArray defendantsArray) {
        final List<Defendant> defendants = new ArrayList();

        for (final JsonValue defendantObject : defendantsArray) {
            final JsonObject defendantJsonObject = (JsonObject) defendantObject;
            Defendant defendant = new Defendant();

            defendant.setId(getValue(defendantJsonObject,ID));
            if(nonNull(getValue(defendantJsonObject,BAIL_STATUS))) {
                defendant.setBailStatus(defendantJsonObject.getString(BAIL_STATUS));
            }

            if(nonNull(getValue(defendantJsonObject,INTERPRETER_LANGUAGE))) {
                defendant.setInterpreterLanguage(getValue(defendantJsonObject,INTERPRETER_LANGUAGE));
            }

            if(nonNull(getValue(defendantJsonObject,INTERPRETER_INFORMATION))) {
                defendant.setInterpreterInformation(getValue(defendantJsonObject,INTERPRETER_INFORMATION));
            }

            final JsonArray hearingsArray = defendantJsonObject.getJsonArray(HEARINGS);
            final JsonArray hearingsArrayWithoutDuplicates = nonNull(hearingsArray) ? removeDuplicateObjects(hearingsArray):null;
            List<Hearing> hearings = new ArrayList<>();
            if (nonNull(hearingsArrayWithoutDuplicates)) {
                getHearings(hearingsArrayWithoutDuplicates, hearings, defendant);
            }

            final JsonObject defendantOrganisationObj = defendantJsonObject.getJsonObject(DEFENDANT_ORGANISATION);
            if(nonNull(defendantOrganisationObj)) {
                createDefendantOrganisation(defendantOrganisationObj, defendant);
            }

            final JsonObject personObject = defendantJsonObject.getJsonObject(DEFENDANT_PERSON);
            if(nonNull(personObject)) {
                createDefendantPerson(personObject, defendant);
            }

            defendants.add(defendant);
        }
        return defendants;
    }

    private static void getHearings(final JsonArray hearingsArrayWithoutDuplicates, final List<Hearing> hearings, final Defendant defendant) {
        for (final JsonValue hearingObject : hearingsArrayWithoutDuplicates) {
            final JsonObject hearingJsonObject = (JsonObject) hearingObject;

            Hearing defendantHearingsInner = new Hearing();
            if (nonNull(getValue(hearingJsonObject, COURT_CENTRE))) {
                defendantHearingsInner.setCourtHouse(hearingJsonObject.getString(COURT_CENTRE));
            }

            if (nonNull(getValue(hearingJsonObject, HEARING_DATE))) {
                defendantHearingsInner.setHearingDate(hearingJsonObject.getString(HEARING_DATE));
            }
            hearings.add(defendantHearingsInner);
        }

        defendant.setHearings(hearings);
    }

    private static void createDefendantPerson(final JsonObject personObject, final Defendant defendant) {
        final DefendantPerson defendantPerson = new DefendantPerson();
        defendantPerson.setForename(getValue(personObject,FORENAME));
        defendantPerson.setSurname(getValue(personObject,SURNAME));
        if(nonNull(getValue(personObject,MIDDLENAME))) {
            defendantPerson.setMiddleName(getValue(personObject, MIDDLENAME));
        }
        if(nonNull(getValue(personObject,DOB))) {
            defendantPerson.setDateOfBirth(getValue(personObject, DOB));
        }
        defendant.setDefendantPerson(defendantPerson);
    }

    private static void createDefendantOrganisation(final JsonObject defendantOrganisationObj, final Defendant defendant) {
        final DefendantOrganisation defendantOrganisation = new DefendantOrganisation();
        defendantOrganisation.setName(getValue(defendantOrganisationObj,NAME));
        defendant.setDefendantOrganisation(defendantOrganisation);
    }

    private static String getValue(final JsonObject jsonObject, final String key) {
        if (nonNull(jsonObject)) {
            return getString(jsonObject, key).orElse(null);
        }
        return null;
    }

    public static JsonArray removeDuplicateObjects(JsonArray jsonArray) {
        Set<String> uniqueHearingSet = new HashSet<>();
        JsonArrayBuilder builder = Json.createArrayBuilder();

        for (JsonValue value : jsonArray) {
            String jsonString = value.toString();
            if (uniqueHearingSet.add(jsonString)) {
                builder.add(value);
            }
        }

        return builder.build();
    }
}

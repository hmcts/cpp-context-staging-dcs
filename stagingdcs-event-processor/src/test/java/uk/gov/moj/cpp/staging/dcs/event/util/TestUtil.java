package uk.gov.moj.cpp.staging.dcs.event.util;

import static java.util.UUID.randomUUID;
import static javax.json.Json.createArrayBuilder;
import static javax.json.Json.createObjectBuilder;

import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.BailStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.Defendant;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.Person;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.PersonDefendant;

import java.time.LocalDate;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class TestUtil {

    public static JsonObject createProsecutionCaseObject(String caseId, String defendantId) {
        final Defendant defendant = getDefendant(defendantId);
        JsonObjectBuilder defendantJsonObjectBuilder = createObjectBuilder()
                .add("id", defendant.id().toString());

        if (defendant.personDefendant() != null) {
            JsonObjectBuilder personDefendantJsonObjectBuilder = createObjectBuilder()
                    .add("id", defendantId)
                    .add("personDefendant", Json.createObjectBuilder()
                            .add("bailStatus", defendant.personDefendant().bailStatus().description())
                            .add("personDetails", Json.createObjectBuilder()
                                    .add("dateOfBirth", defendant.personDefendant().personDetails().dateOfBirth().toString())
                                    .add("firstName", defendant.personDefendant().personDetails().firstName())
                                    .add("interpreterLanguageNeeds", defendant.personDefendant().personDetails().interpreterLanguageNeeds())
                                    .add("lastName", defendant.personDefendant().personDetails().lastName())
                                    .add("middleName", defendant.personDefendant().personDetails().middleName())
                                    .add("nationalInsuranceNumber", defendant.personDefendant().personDetails().nationalInsuranceNumber())
                                    .add("title", defendant.personDefendant().personDetails().title())
                            ).build());
            defendantJsonObjectBuilder.add("personDefendant", personDefendantJsonObjectBuilder);
        }

        if (defendant.legalEntityDefendant() != null) {
            JsonObjectBuilder legalEntityDefendantJsonObjectBuilder = createObjectBuilder()
                    .add("legalEntityDefendant", defendant.legalEntityDefendant().toString());
            defendantJsonObjectBuilder.add("legalEntityDefendant", legalEntityDefendantJsonObjectBuilder);
        }

        final JsonObjectBuilder prosecutionCaseBuilder = createObjectBuilder()
                .add("id", caseId)
                .add("defendants", createArrayBuilder().add(defendantJsonObjectBuilder).build())
                .add("prosecutionCaseIdentifier", createObjectBuilder().add("caseUrn", "XJDKMD").build());
        return Json.createObjectBuilder()
                .add("prosecutionCase", prosecutionCaseBuilder.build())
                .build();
    }
    public static Defendant getDefendant(final String defendantId) {
        Person personDetails = new Person(
                LocalDate.of(1980, 1, 1),
                "DummyFirstName",
                "English",
                "DummyLastName",
                "DummyMiddleName",
                "AB123456C",
                "Mr"
        );

        BailStatus bailStatus = new BailStatus("code", "description", randomUUID());
        PersonDefendant personDefendant = new PersonDefendant(bailStatus, personDetails);

        return new Defendant(UUID.fromString(defendantId), UUID.fromString(defendantId), personDefendant, null);
    }

    public static JsonObject createAssociationObject() {
        return Json.createObjectBuilder()
                .add("association", Json.createObjectBuilder()
                        .add("organisationId", randomUUID().toString())
                        .add("organisationName", "Harry & Co LLP")
                        .add("address", Json.createObjectBuilder()
                                .add("address1", "Legal House")
                                .add("address2", "15 Sewell Street")
                                .add("address3", "Hammersmith")
                                .add("address4", "London")
                                .add("addressPostcode", "SE14 2AB"))
                        .add("status", "Active Barrister/Solicitor of record")
                        .add("startDate", "2020-04-19T00:00:00.000Z")
                        .add("representationType", "PRIVATE"))
                .build();

    }

    public static JsonObject createOrganisationObject() {
        return createObjectBuilder()
                .add("name", "test ltd")
                .add("email", "test@test.com")
                .build();
    }
}

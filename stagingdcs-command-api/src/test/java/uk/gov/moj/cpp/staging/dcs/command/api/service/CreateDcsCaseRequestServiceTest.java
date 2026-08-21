package uk.gov.moj.cpp.staging.dcs.command.api.service;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.BAIL_STATUS;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_REFERRAL;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_URN;
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

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.DcsCaseCreateRequest;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.DcsOffence;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.DcsDefendant;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.DefendantOrganisation;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.OffenceDetails;
import uk.gov.moj.cpp.staging.dcs.event.service.ReferenceDataService;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.CaseDefendantOffencesEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.CaseDefendantOffencesRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsDefendantRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class CreateDcsCaseRequestServiceTest {

    @InjectMocks
    private CreateDcsCaseRequestService createDcsCaseRequestService;

    @Mock
    private Logger logger;

    @Mock
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Mock
    private CaseDefendantOffencesRepository caseDefendantOffencesRepository;

    @Mock
    private UtcClock clock;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private DcsDefendantRepository dcsDefendantRepository;

    @Test
    void shouldCallIsCaseLinkedAndReturnFalse() {

        boolean result = createDcsCaseRequestService.isCaseUnlinkedOrFailed(randomUUID());
        assertFalse(result);
        verify(dcsCaseDetailRepository, times(1)).findByCaseId(any(UUID.class));
    }

    @Test
    void shouldCallIsCaseLinkedAndReturnTrue() {

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setDcsDefendantStatus("UNLINKED");

        when(dcsCaseDetailRepository.findByCaseId(any(UUID.class))).thenReturn(Arrays.asList(dcsCaseDetailEntity));
        boolean result = createDcsCaseRequestService.isCaseUnlinkedOrFailed(randomUUID());
        assertTrue(result);
        verify(dcsCaseDetailRepository, times(1)).findByCaseId(any(UUID.class));
    }

    @Test
    void shouldCreateDcsCase() {

        final List<DcsOffence> addedOffences = Arrays.asList(DcsOffence.Builder.newOffence()
                .withOffenceCode("OffenceCode1")
                .withOffenceId(randomUUID())
                .build());

        final OffenceDetails offenceDetails = OffenceDetails.Builder.newOffence()
                .withAddedOffences(addedOffences)
                .build();

        final DcsDefendant defendant = DcsDefendant.Builder.newDefendant()
                .withDefendantOrganisation(DefendantOrganisation.Builder.newDefendantOrganisation()
                        .withName("Org 1")
                        .build())
                .withOffencesDetails(offenceDetails)
                .build();

        final DcsCaseCreateRequest dcsCaseCreateRequest = DcsCaseCreateRequest.Builder.newDcsCaseCreateRequest().build();

        createDcsCaseRequestService.createDcsCase (dcsCaseCreateRequest,defendant);
        verify(dcsCaseDetailRepository, times(1)).saveDcsCaseDetail(any(DcsCaseDetailEntity.class));
    }

    @Test
    void shouldUpdateExistingCase() {

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(any(), any())).thenReturn(new DcsCaseDetailEntity());
        final UUID offenceId = randomUUID();

        final List<CaseDefendantOffencesEntity> caseDefendantOffencesEntities = new ArrayList<>();
        final CaseDefendantOffencesEntity caseDefendantOffencesEntity = new CaseDefendantOffencesEntity();
        caseDefendantOffencesEntity.setId(randomUUID());
        caseDefendantOffencesEntity.setOffenceId(offenceId);
        caseDefendantOffencesEntities.add(caseDefendantOffencesEntity);
        when(caseDefendantOffencesRepository.findByCaseIdDefendantId(any(),any())).thenReturn(caseDefendantOffencesEntities);

        final List<DcsOffence> addedOffences = Arrays.asList(DcsOffence.Builder.newOffence()
                .withOffenceCode("OffenceCode1")
                .withOffenceId(randomUUID())
                .build());

        final List<DcsOffence> removedOffences = Arrays.asList(DcsOffence.Builder.newOffence()
                .withOffenceCode("OffenceCode0")
                .withOffenceId(offenceId)
                .build());

        final OffenceDetails offenceDetails = OffenceDetails.Builder.newOffence()
                .withAddedOffences(addedOffences)
                .withRemovedOffences(removedOffences)
                .build();

        final DcsDefendant defendant = DcsDefendant.Builder.newDefendant()
                .withOffencesDetails(offenceDetails)
                .build();

        final DcsCaseCreateRequest dcsCaseCreateRequest = DcsCaseCreateRequest.Builder.newDcsCaseCreateRequest().build();

        createDcsCaseRequestService.createDcsCase (dcsCaseCreateRequest,defendant);
        verify(caseDefendantOffencesRepository, times(1)).saveCaseDefendantOffence(any(CaseDefendantOffencesEntity.class));
        verify(caseDefendantOffencesRepository, times(1)).deleteCaseDefendantOffences(any());
    }

    @Test
    void shouldNotTransformPayloadWhenAlreadyLinked() {
        final String caseId = randomUUID().toString();
        final String defendantId = randomUUID().toString();
        final UUID caseRefId = randomUUID();
        final JsonObject payload = createObjectBuilder()
                .add(CASE_ID, caseId)
                .add(CASE_URN, "test123")
                .add(PROSECUTION_AUTHORITY, "CPS")
                .add(COURT_CENTRE, "Southwark")
                .add(HEARING_DATE, "2025-01-21")
                .add(DEFENDANTS, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(ID, defendantId)
                                .build()
                        )
                        .build())
                .build();

        final DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(UUID.fromString(caseId));
        dcsCaseDetailEntity.setCaseRefId(caseRefId);
        dcsCaseDetailEntity.setDefendantId(UUID.fromString(defendantId));
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.toString());
        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId),UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);
        when(dcsCaseDetailRepository.findByCaseId(UUID.fromString(caseId))).thenReturn(List.of(dcsCaseDetailEntity));

        final JsonObject transformedPayload = createDcsCaseRequestService.transformPayload(payload);
        assertTrue(transformedPayload.getJsonArray(DEFENDANTS).isEmpty());
        assertTrue(transformedPayload.getString(CASE_REFERRAL).equals(caseRefId.toString()));
    }

    @Test
    void shouldTransformPayloadWhenNotLinkedWithCpsProsecutor() {
        final String caseId = randomUUID().toString();
        final String defendantId = randomUUID().toString();

        final JsonObject payload = createObjectBuilder()
                .add(CASE_ID, caseId)
                .add(CASE_URN, "test123")
                .add(PROSECUTION_AUTHORITY, "CPS")
                .add(DEFENDANTS, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(ID, defendantId)
                                .add(BAIL_STATUS, "Remand")
                                .add(INTERPRETER_LANGUAGE, "German")
                                .add(INTERPRETER_INFORMATION, "Interpreter Required")
                                .add(DEFENDANT_ORGANISATION, createObjectBuilder()
                                        .add(NAME, "Org1")
                                        .build())
                                .build()
                        )
                        .build())
                .build();

        final DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId),UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);
        final JsonObjectBuilder jsonProsecutorBuilder = createObjectBuilder();
        jsonProsecutorBuilder
                .add("cpsFlag", true)
                .add("policeFlag", false);
        when(referenceDataService.getProsecutorByProsecutionAuthority(any())).thenReturn(Optional.of(jsonProsecutorBuilder.build()));

        final JsonObject transformedPayload = createDcsCaseRequestService.transformPayload(payload);
        assertTrue(transformedPayload.getJsonArray(DEFENDANTS).size() == 1);
        assertTrue(transformedPayload.getString(PROSECUTION_AUTHORITY).equals("CPS"));
        assertFalse(transformedPayload.containsKey(CASE_REFERRAL));
    }

    @Test
    void shouldTransformPayloadWhenNotLinkedWithOtherProsecutor() {
        final String caseId = randomUUID().toString();
        final String defendantId = randomUUID().toString();

        final JsonObject payload = createObjectBuilder()
                .add(CASE_ID, caseId)
                .add(CASE_URN, "test123")
                .add(PROSECUTION_AUTHORITY, "CPS")
                .add(DEFENDANTS, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(ID, defendantId)
                                .add(BAIL_STATUS, "Remand")
                                .add(INTERPRETER_LANGUAGE, "German")
                                .add(INTERPRETER_INFORMATION, "Interpreter Required")
                                .add(HEARINGS, createArrayBuilder()
                                        .add(createObjectBuilder()
                                                .add(COURT_CENTRE, "Southwark")
                                                .add(HEARING_DATE, "2025-01-21")
                                                .build())
                                        .build())
                                .add(DEFENDANT_ORGANISATION, createObjectBuilder()
                                        .add(NAME, "Org1")
                                        .build())
                                .build()
                        )
                        .build())
                .build();

        final DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId),UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);
        final JsonObjectBuilder jsonProsecutorBuilder = createObjectBuilder();
        jsonProsecutorBuilder
                .add("cpsFlag", false)
                .add("policeFlag", true);
        when(referenceDataService.getProsecutorByProsecutionAuthority(any())).thenReturn(Optional.of(jsonProsecutorBuilder.build()));

        final JsonObject transformedPayload = createDcsCaseRequestService.transformPayload(payload);
        assertTrue(transformedPayload.getJsonArray(DEFENDANTS).size() == 1);
        assertTrue(transformedPayload.getString(PROSECUTION_AUTHORITY).equals("OTHER"));
        assertFalse(transformedPayload.containsKey(CASE_REFERRAL));
    }

    @Test
    void shouldTransformPayloadWhenNotLinkedWithProbationProsecutor() {
        final String caseId = randomUUID().toString();
        final String defendantId = randomUUID().toString();

        final JsonObject payload = createObjectBuilder()
                .add(CASE_ID, caseId)
                .add(CASE_URN, "test123")
                .add(PROSECUTION_AUTHORITY, "CPS")
                .add(DEFENDANTS, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(ID, defendantId)
                                .add(BAIL_STATUS, "Remand")
                                .add(INTERPRETER_LANGUAGE, "German")
                                .add(INTERPRETER_INFORMATION, "Interpreter Required")
                                .add(DEFENDANT_PERSON, createObjectBuilder()
                                        .add(FORENAME, "foreName")
                                        .add(MIDDLENAME, "middleName")
                                        .add(SURNAME, "surName")
                                        .add(DOB, "2001-02-01")
                                        .build())
                                .build()
                        )
                        .build())
                .build();

        final DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId),UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);
        final JsonObjectBuilder jsonProsecutorBuilder = createObjectBuilder();
        jsonProsecutorBuilder
                .add("cpsFlag", false)
                .add("probationFlag", true)
                .add("policeFlag", true);
        when(referenceDataService.getProsecutorByProsecutionAuthority(any())).thenReturn(Optional.of(jsonProsecutorBuilder.build()));

        final JsonObject transformedPayload = createDcsCaseRequestService.transformPayload(payload);
        assertTrue(transformedPayload.getJsonArray(DEFENDANTS).size() == 1);
        assertTrue(transformedPayload.getString(PROSECUTION_AUTHORITY).equals("PROBATION"));
    }

    @Test
    void shouldTransformPayloadWhenCaseIsLinkedButNewDefendantAdded() {
        final String caseId = randomUUID().toString();
        final String defendantId = randomUUID().toString();
        final UUID caseRefId = randomUUID();

        final JsonObject payload = createObjectBuilder()
                .add(CASE_ID, caseId)
                .add(CASE_URN, "test123")
                .add(PROSECUTION_AUTHORITY, "CPS")
                .add(DEFENDANTS, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(ID, defendantId)
                                .add(BAIL_STATUS, "Remand")
                                .add(INTERPRETER_LANGUAGE, "German")
                                .add(INTERPRETER_INFORMATION, "Interpreter Required")
                                .add(DEFENDANT_PERSON, createObjectBuilder()
                                        .add(FORENAME, "foreName")
                                        .add(MIDDLENAME, "middleName")
                                        .add(SURNAME, "surName")
                                        .add(DOB, "2001-02-01")
                                        .build())
                                .build()
                        )
                        .build())
                .build();

        final DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId),UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);
        final DcsCaseDetailEntity caseEntity = new DcsCaseDetailEntity();
        caseEntity.setCaseRefId(caseRefId);
        caseEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByCaseId(UUID.fromString(caseId))).thenReturn(List.of(caseEntity));
        final JsonObjectBuilder jsonProsecutorBuilder = createObjectBuilder();
        jsonProsecutorBuilder
                .add("cpsFlag", false)
                .add("probationFlag", true)
                .add("policeFlag", true);
        when(referenceDataService.getProsecutorByProsecutionAuthority(any())).thenReturn(Optional.of(jsonProsecutorBuilder.build()));

        final JsonObject transformedPayload = createDcsCaseRequestService.transformPayload(payload);
        assertTrue(transformedPayload.getJsonArray(DEFENDANTS).size() == 1);
        assertTrue(transformedPayload.getString(PROSECUTION_AUTHORITY).equals("PROBATION"));
        assertTrue(transformedPayload.getString(CASE_REFERRAL).equals(caseRefId.toString()));
    }

}

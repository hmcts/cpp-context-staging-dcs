package uk.gov.moj.cpp.staging.dcs.command.api.service;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createArrayBuilder;
import static javax.json.Json.createObjectBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.getString;
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
import static uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus.LINKED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus.UNLINKED;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.DcsCaseCreateRequest;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.DcsOffence;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.DcsDefendant;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.OffenceDetails;
import uk.gov.moj.cpp.staging.dcs.event.service.ReferenceDataService;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.CaseDefendantOffencesEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsDefendantEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.CaseDefendantOffencesRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsDefendantRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.transaction.Transactional;

import org.slf4j.Logger;

@SuppressWarnings({"squid:S6813", "squid:S1125"})
@Transactional
public class CreateDcsCaseRequestService {

    public static final String DCS_CPS = "CPS";
    public static final String DCS_PROBATION = "PROBATION";
    public static final String DCS_OTHER = "OTHER";
    public static final String PROBATION_FLAG = "probationFlag";
    public static final String CPS_FLAG = "cpsFlag";

    @Inject
    private Logger logger;

    @Inject
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Inject
    private CaseDefendantOffencesRepository caseDefendantOffencesRepository;

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    private UtcClock clock;
    @Inject
    private DcsDefendantRepository dcsDefendantRepository;

    public boolean isCaseUnlinkedOrFailed(final UUID caseId) {
        final List<DcsCaseDetailEntity> dcsCaseDetailEntities = dcsCaseDetailRepository.findByCaseId(caseId);

        final Optional<DcsCaseDetailEntity> entity = dcsCaseDetailEntities.stream()
                .filter(dcsCaseDetailEntity -> (dcsCaseDetailEntity.getDcsDefendantStatus().equalsIgnoreCase(UNLINKED.toString()) || dcsCaseDetailEntity.getDcsDefendantStatus().equalsIgnoreCase(DcsDefendantStatus.FAILED.toString())))
                .findAny();

        return entity.isPresent();
    }

    public void createDcsCase(final DcsCaseCreateRequest dcsCaseCreateRequest, final DcsDefendant defendant) {
        logger.info("Starting to create case in DCS. caseId: {}, defendantId: {}", dcsCaseCreateRequest.getCaseId(), defendant.getId());
        final UUID caseId = dcsCaseCreateRequest.getCaseId();

        if (ofNullable(dcsCaseDetailRepository.findByCaseIdDefendantId(dcsCaseCreateRequest.getCaseId(), defendant.getId())).isEmpty()) {
            saveCaseDetail(dcsCaseCreateRequest, defendant);
            addOffences(dcsCaseCreateRequest, defendant);
            saveDcsDefendant(defendant);
        } else {
            final OffenceDetails offenceDetails = defendant.getOffencesDetails();
            if(nonNull(offenceDetails) && offenceDetails.getRemovedOffences() != null) {
                processRemovedOffences(defendant, caseId);
            }

            if(nonNull(offenceDetails) && offenceDetails.getAddedOffences() != null) {
                processAddedOffences(dcsCaseCreateRequest, defendant, caseId);
            }
        }
    }

    private void processAddedOffences(final DcsCaseCreateRequest dcsCaseCreateRequest, final DcsDefendant defendant, final UUID caseId) {
        final List<CaseDefendantOffencesEntity> caseDefendantOffencesEntities = caseDefendantOffencesRepository.findByCaseIdDefendantId(caseId, defendant.getId());

        defendant.getOffencesDetails().getAddedOffences().stream()
                .forEach(addedOffence -> {
                    boolean isPresent = caseDefendantOffencesEntities.stream()
                            .anyMatch(entity -> entity.getOffenceId().equals(addedOffence.getOffenceId()));

                    if (!isPresent) {
                        addOffence(dcsCaseCreateRequest, defendant, addedOffence);
                    }
                });
    }

    private void processRemovedOffences(final DcsDefendant defendant, final UUID caseId) {
        Set<UUID> idsToDelete = new HashSet<>();
        final List<CaseDefendantOffencesEntity> caseDefendantOffencesEntities = caseDefendantOffencesRepository.findByCaseIdDefendantId(caseId, defendant.getId());

        defendant.getOffencesDetails().getRemovedOffences().stream()
                .forEach(removedOffence -> {
                    boolean isPresent = caseDefendantOffencesEntities.stream()
                            .anyMatch(entity -> entity.getOffenceId().equals(removedOffence.getOffenceId()));

                    if (isPresent) {
                        final Optional<CaseDefendantOffencesEntity> caseDefendantOffencesEntity = caseDefendantOffencesEntities.stream()
                                .filter(entity -> entity.getOffenceId().equals(removedOffence.getOffenceId()))
                                .findFirst();
                        idsToDelete.add(caseDefendantOffencesEntity.get().getId());
                    }
                });

        if(!idsToDelete.isEmpty()) {
            caseDefendantOffencesRepository.deleteCaseDefendantOffences(new ArrayList<>(idsToDelete));
        }
    }

    private void addOffence(final DcsCaseCreateRequest dcsCaseCreateRequest, final DcsDefendant defendant, final DcsOffence addedOffence) {
        final CaseDefendantOffencesEntity caseDefendantOffencesEntity = new CaseDefendantOffencesEntity();
        caseDefendantOffencesEntity.setId(randomUUID());
        caseDefendantOffencesEntity.setCaseId(dcsCaseCreateRequest.getCaseId());
        caseDefendantOffencesEntity.setDefendantId(defendant.getId());
        caseDefendantOffencesEntity.setOffenceId(addedOffence.getOffenceId());

        caseDefendantOffencesRepository.saveCaseDefendantOffence(caseDefendantOffencesEntity);
    }

    private void addOffences(final DcsCaseCreateRequest dcsCaseCreateRequest, final DcsDefendant defendant) {
        if(nonNull(defendant.getOffencesDetails()) && defendant.getOffencesDetails().getAddedOffences() != null) {
            defendant.getOffencesDetails().getAddedOffences().stream()
                    .forEach(offence -> addOffence(dcsCaseCreateRequest, defendant, offence));
        }
    }

    private void saveCaseDetail(final DcsCaseCreateRequest dcsCaseCreateRequest, final DcsDefendant defendant) {
        final DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(dcsCaseCreateRequest.getCaseId());
        dcsCaseDetailEntity.setCaseUrn(dcsCaseCreateRequest.getCaseUrn());
        dcsCaseDetailEntity.setDefendantId(defendant.getId());
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.PENDING.toString());
        dcsCaseDetailEntity.setCreatedAt(clock.now());
        dcsCaseDetailRepository.saveDcsCaseDetail(dcsCaseDetailEntity);
    }

    private void saveDcsDefendant(final DcsDefendant defendant) {
        logger.info("Saving defendant details for defendantId: {}", defendant.getId());
        final DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(defendant.getId());
        dcsDefendantEntity.setBailStatus(defendant.getBailStatus());
        dcsDefendantEntity.setInterpreterInformation(defendant.getInterpreterLanguage());
        dcsDefendantEntity.setInterpreterInformation(defendant.getInterpreterInformation());
        if(nonNull(defendant.getDefendantPerson())) {
            dcsDefendantEntity.setForename(defendant.getDefendantPerson().getForename());
            dcsDefendantEntity.setMiddlename(defendant.getDefendantPerson().getMiddleName());
            dcsDefendantEntity.setSurname(defendant.getDefendantPerson().getSurname());
            dcsDefendantEntity.setDateOfBirth(defendant.getDefendantPerson().getDateOfBirth());
        } else {
            dcsDefendantEntity.setOrganisationName(defendant.getDefendantOrganisation().getName());
        }
        dcsDefendantEntity.setCreatedAt(clock.now());
        dcsDefendantRepository.saveDefendant(dcsDefendantEntity);
    }

    public JsonObject transformPayload(final JsonObject payload) {
        final String caseId = payload.getString(CASE_ID);
        final JsonObjectBuilder transformedPayload = Json.createObjectBuilder()
                .add(CASE_ID, caseId)
                .add(CASE_URN, payload.getString(CASE_URN))
                .add(PROSECUTION_AUTHORITY, getProsecutorType(payload.getString(PROSECUTION_AUTHORITY)));

        final JsonArray defendantsArray = payload.getJsonArray(DEFENDANTS);
        final JsonArrayBuilder transformedDefendantsArray = createArrayBuilder();
        String caseReferral = getCaseReferralForLinkedCase(caseId);
        if (nonNull(caseReferral)) {
            transformedPayload.add(CASE_REFERRAL, caseReferral);
        }

        for (final JsonValue defendantObject : defendantsArray) {
            final JsonObject defendantJsonObject = (JsonObject) defendantObject;
            final String defendantId = getValue(defendantJsonObject, ID);

            final DcsCaseDetailEntity dcsCaseDetailEntity = dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId), UUID.fromString(defendantId));
            final boolean isDefendantAlreadyLinked = nonNull(dcsCaseDetailEntity.getDcsDefendantStatus()) ? dcsCaseDetailEntity.getDcsDefendantStatus().equalsIgnoreCase(LINKED.toString()) : false;

            if (!isDefendantAlreadyLinked) {
                addDefendant(defendantId, defendantJsonObject, transformedDefendantsArray);
            }
        }
        transformedPayload.add(DEFENDANTS, transformedDefendantsArray.build());
        return transformedPayload.build();
    }

    private String getCaseReferralForLinkedCase(final String caseId){
        return dcsCaseDetailRepository.findByCaseId(fromString(caseId)).stream()
                .filter(entity -> LINKED.toString().equalsIgnoreCase(entity.getDcsDefendantStatus()))
                .map(entity -> entity.getCaseRefId().toString())
                .findFirst().orElse(null);
    }

    private void addDefendant(final String defendantId, final JsonObject defendantJsonObject, final JsonArrayBuilder transformedDefendantsArray) {
        JsonObjectBuilder defendantBuilder = createObjectBuilder()
                .add(ID, defendantId);

        if (nonNull(getValue(defendantJsonObject, BAIL_STATUS))) {
            defendantBuilder.add(BAIL_STATUS, defendantJsonObject.getString(BAIL_STATUS));
        }

        if (nonNull(getValue(defendantJsonObject, INTERPRETER_LANGUAGE))) {
            defendantBuilder.add(INTERPRETER_LANGUAGE, getValue(defendantJsonObject, INTERPRETER_LANGUAGE));
        }

        if (nonNull(getValue(defendantJsonObject, INTERPRETER_INFORMATION))) {
            defendantBuilder.add(INTERPRETER_INFORMATION, getValue(defendantJsonObject, INTERPRETER_INFORMATION));
        }

        final JsonArray hearingsArray = defendantJsonObject.getJsonArray(HEARINGS);
        final JsonArrayBuilder transformedHearingsArray = createArrayBuilder();
        if (nonNull(hearingsArray)) {
            processHearings(hearingsArray, transformedHearingsArray, defendantBuilder);
        }

        final JsonObject defendantOrganisationObj = defendantJsonObject.getJsonObject(DEFENDANT_ORGANISATION);
        addOrgDefendant(defendantOrganisationObj, defendantBuilder);

        final JsonObject personObject = defendantJsonObject.getJsonObject(DEFENDANT_PERSON);
        addPersonDefendant(personObject, defendantBuilder);
        transformedDefendantsArray.add(defendantBuilder.build());
    }

    private void processHearings(final JsonArray hearingsArray, final JsonArrayBuilder transformedHearingsArray, final JsonObjectBuilder defendantBuilder) {
        for (final JsonValue hearingObject : hearingsArray) {
            final JsonObject hearingJsonObject = (JsonObject) hearingObject;

            JsonObjectBuilder hearingBuilder = createObjectBuilder();

            if (nonNull(getValue(hearingJsonObject, COURT_CENTRE))) {
                hearingBuilder.add(COURT_CENTRE, hearingJsonObject.getString(COURT_CENTRE));
            }

            if (nonNull(getValue(hearingJsonObject, HEARING_DATE))) {
                hearingBuilder.add(HEARING_DATE, hearingJsonObject.getString(HEARING_DATE));
            }
            transformedHearingsArray.add(hearingBuilder.build());
        }

        defendantBuilder.add(HEARINGS, transformedHearingsArray.build());
    }

    private void addOrgDefendant(final JsonObject defendantOrganisationObj, final JsonObjectBuilder defendantBuilder) {
        if (nonNull(defendantOrganisationObj)) {
            final JsonObject organisation = createObjectBuilder()
                    .add(NAME, getValue(defendantOrganisationObj, NAME))
                    .build();
            defendantBuilder.add(DEFENDANT_ORGANISATION, organisation);
        }
    }

    private void addPersonDefendant(final JsonObject personObject, final JsonObjectBuilder defendantBuilder) {
        if (nonNull(personObject)) {
            final JsonObjectBuilder personBuilder = createObjectBuilder()
                    .add(FORENAME, getValue(personObject, FORENAME))
                    .add(SURNAME, getValue(personObject, SURNAME));

            if (nonNull(getValue(personObject, MIDDLENAME))) {
                personBuilder.add(MIDDLENAME, getValue(personObject, MIDDLENAME));
            }
            if (nonNull(getValue(personObject, DOB))) {
                personBuilder.add(DOB, getValue(personObject, DOB));
            }
            defendantBuilder.add(DEFENDANT_PERSON, personBuilder.build());
        }
    }

    private static String getValue(final JsonObject jsonObject, final String key) {
        if (nonNull(jsonObject)) {
            return getString(jsonObject, key).orElse(null);
        }
        return null;
    }

    private String getProsecutorType(final String prosecutorAuthorityCode) {
        final Optional<JsonObject> prosecutorByProsecutionAuthority = referenceDataService.getProsecutorByProsecutionAuthority(prosecutorAuthorityCode);
        if (prosecutorByProsecutionAuthority.isPresent()) {
            JsonObject prosecutors = prosecutorByProsecutionAuthority.get();
            final boolean probationFlag = prosecutors.getBoolean(PROBATION_FLAG, false);
            final boolean cpsFlag = prosecutors.getBoolean(CPS_FLAG, false);


            if (cpsFlag) {
                return DCS_CPS;
            }

            if (probationFlag) {
                return DCS_PROBATION;
            }
        }
        return DCS_OTHER;
    }

}

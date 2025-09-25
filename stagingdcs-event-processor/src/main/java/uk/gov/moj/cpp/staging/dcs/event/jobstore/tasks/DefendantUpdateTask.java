package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.fromString;
import static javax.json.Json.createObjectBuilder;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DEFENDANT_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.METADATA;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.TRANSACTION_REF;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.FAILED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.PENDING;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.RETRY;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.DEFENDANT_UPDATE_TASK;

import uk.gov.hmcts.dcs.openapi.model.BadRequestErrorResponsePayload;
import uk.gov.hmcts.dcs.openapi.model.Defendant;
import uk.gov.hmcts.dcs.openapi.model.DefendantOrganisation;
import uk.gov.hmcts.dcs.openapi.model.DefendantPerson;
import uk.gov.hmcts.dcs.openapi.model.ErrorResponsePayload;
import uk.gov.hmcts.dcs.openapi.model.UpdateDefendantDetailsRequest;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.moj.cpp.jobstore.api.annotation.Task;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.Person;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.PersonDefendant;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.ProsecutionCase;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsNotificationHelper;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsResponseProcessingException;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.ProsecutionCaseHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsRestNotificationService;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsDefendantEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionMetadataEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsDefendantRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Task(DEFENDANT_UPDATE_TASK)
public class DefendantUpdateTask extends BaseTask implements ExecutableTask {

    public static final String ERROR_MESSAGE = "Exception processing the bad request response: ";
    @Inject
    private DcsRestNotificationService dcsRestNotificationService;
    @Inject
    private DcsNotificationHelper dcsNotificationHelper;
    @Inject
    private TransactionDetailRepository transactionDetailRepository;
    @Inject
    private TransactionMetadataRepository transactionMetadataRepository;
    @Inject
    private DcsCaseDetailRepository dcsCaseDetailRepository;
    @Inject
    private ProsecutionCaseHelper prosecutionCaseHelper;
    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;
    @Inject
    private DcsDefendantRepository dcsDefendantRepository;
    @Inject
    private DcsOperationHelper dcsOperationHelper;

    @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        return Optional.of(retryConfiguration.getTaskRetryDurationsSeconds());
    }

    @Override
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {

        final JsonObject jobData = executionInfo.getJobData();
        final String caseId = jobData.getString(CASE_ID);
        final String defendantId = jobData.getString(DEFENDANT_ID);
        final JsonObject metadataObject = jobData.getJsonObject(METADATA);
        final String transactionRef = jobData.getString(TRANSACTION_REF);

        final ProsecutionCase prosecutionCase = prosecutionCaseHelper.getProsecutionCase(metadataObject, UUID.fromString(caseId));
        logger.info("-----buildDefendant-------");
        Defendant apiDefendant = buildDefendant(prosecutionCase, defendantId);

        final boolean isDefendantAlreadyPresent = isDefendantAlreadyPresent(apiDefendant, defendantId);
        logger.info("isDefendantAlreadyPresent for caseId {}, defendantId:{}, {}", isDefendantAlreadyPresent, caseId, defendantId);

        if (!isDefendantAlreadyPresent) {
            logger.info("Sending Defendant Updates for caseId: {}, defendantId: {}", caseId, defendantId);
           final DcsCaseDetailEntity dcsCaseDetailEntity = dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId), UUID.fromString(defendantId));

           UpdateDefendantDetailsRequest updateDefendantDetailsRequest = new UpdateDefendantDetailsRequest();
           updateDefendantDetailsRequest.setDefendant(apiDefendant);
           updateDefendantDetailsRequest.setCaseReferral(String.valueOf(dcsCaseDetailEntity.getCaseRefId()));
           updateDefendantDetailsRequest.setTransactionRef(transactionRef);
           updateDefendantDetailsRequest.setCaseId(caseId);
           String caseUrn = dcsCaseDetailEntity.getCaseUrn();
           String defendantReferral = String.valueOf(dcsCaseDetailEntity.getDefendantRefId());

           String payload = String.valueOf(objectToJsonObjectConverter.convert(updateDefendantDetailsRequest));
           try (Response response = dcsRestNotificationService.sendUpdatedDefendantDetails(caseUrn, defendantReferral, payload)) {

               final String messageBody = response.readEntity(String.class);
               logger.info("Response from APIM: {}", messageBody);
               final String finalPayload = payload;
               Map<Integer, Runnable> responseHandlers = Map.of(
                       204, () -> handleSuccess(updateDefendantDetailsRequest, finalPayload),
                       400, () -> handleBadRequest(messageBody, finalPayload, updateDefendantDetailsRequest),
                       404, () -> handleErrors(messageBody, payload, updateDefendantDetailsRequest)
               );

               responseHandlers.getOrDefault(response.getStatus(), () -> handleDefault(messageBody)).run();

           } catch (DcsResponseProcessingException e) {
               return updateStatusAndRetry(payload, updateDefendantDetailsRequest, e.getMessage());
           } catch (Exception e) {
               logger.error("Exception while sending notification : {}", e.getMessage());
               return updateStatusAndRetry(payload, updateDefendantDetailsRequest, e.getMessage());
           }
       } else {
           logger.info("No defendant updates to be send to DCS.");
           updateIncompleteTransactionAndMetadataStatusIfPresent(transactionRef);
       }

        return executionInfo()
                .withExecutionStatus(COMPLETED)
                .build();
    }

    private void updateIncompleteTransactionAndMetadataStatusIfPresent(final String transactionRef) {
        final UUID tranId = fromString(transactionRef);
        List<TransactionMetadataEntity> transactionMetadataEntityList = transactionMetadataRepository.findByTransactionReferenceId(tranId);
        for(TransactionMetadataEntity transactionMetadataEntity : transactionMetadataEntityList) {
           if(RETRY.name().equalsIgnoreCase(transactionMetadataEntity.getTransactionStatus()) || PENDING.name().equalsIgnoreCase(transactionMetadataEntity.getTransactionStatus())) {
                transactionMetadataRepository.updateStatusByTransactionReferenceId(FAILED.name(), tranId);
           }
        }
        TransactionDetailEntity transactionDetailEntity = transactionDetailRepository.findByTransactionReferenceId(tranId);
        if(nonNull(transactionDetailEntity)
                && nonNull(transactionDetailEntity.getTransactionRefId())
                &&(RETRY.name().equalsIgnoreCase(transactionDetailEntity.getTransactionStatus()) || PENDING.name().equalsIgnoreCase(transactionDetailEntity.getTransactionStatus()))){
            transactionDetailRepository.updateStatusByTransactionReferenceId(FAILED.name(), EMPTY, tranId);
        }
    }

    private boolean isDefendantAlreadyPresent(final Defendant apiDefendant, final String defendantId) {
        boolean isDefendantAlreadyPresent;
        final DcsDefendantEntity dcsDefendantEntity = dcsDefendantRepository.findByDefendantId(UUID.fromString(defendantId));
        if(nonNull(apiDefendant.getDefendantPerson()) && nonNull(dcsDefendantEntity)) {
            final DefendantPerson defendantPerson = apiDefendant.getDefendantPerson();
            final String entityDob = nonNull(dcsDefendantEntity.getDateOfBirth()) ? dcsDefendantEntity.getDateOfBirth().toString() : EMPTY;
            isDefendantAlreadyPresent =  Objects.equals(trim(dcsDefendantEntity.getForename()), trim(defendantPerson.getForename())) &&
                    Objects.equals(trim(dcsDefendantEntity.getSurname()), trim(defendantPerson.getSurname())) &&
                    Objects.equals(trim(dcsDefendantEntity.getMiddlename()), trim(defendantPerson.getMiddleName())) &&
                    Objects.equals(entityDob, defendantPerson.getDateOfBirth());
            logger.info("isDefendantAlreadyPresent: {} for Person Defendant: {}", isDefendantAlreadyPresent, defendantId);
        } else {
            final String entityOrgName = nonNull(dcsDefendantEntity) ? dcsDefendantEntity.getOrganisationName():null;
            final String defendantOrgName = nonNull(apiDefendant.getDefendantOrganisation())?apiDefendant.getDefendantOrganisation().getName():null;
            isDefendantAlreadyPresent = Objects.equals(trim(entityOrgName), trim(defendantOrgName));
            logger.info("isDefendantAlreadyPresent: {} for Org Defendant: {}", isDefendantAlreadyPresent, defendantId);
        }
        return isDefendantAlreadyPresent;
    }

    private Defendant buildDefendant(ProsecutionCase prosecutionCase, String defendantId) {
        Defendant apiDefendant = new Defendant();
        if (nonNull(prosecutionCase)) {
            prosecutionCase.defendants().stream()
                    .filter(defendant -> defendant.id().equals(UUID.fromString(defendantId)))
                    .findFirst().ifPresent(defendant -> {
                        logger.info("--------------buildDefendant for defendantId:{}", defendantId);
                        apiDefendant.setId(defendantId);

                        if (defendant.personDefendant() != null) {
                            logger.info("--------------personDefendant---------");
                            DefendantPerson personInfo = getPersonInfo(defendant);
                            apiDefendant.setDefendantPerson(personInfo);
                            logger.info("--------------personInfo updated---------");
                            apiDefendant.setInterpreterInformation(defendant.personDefendant().personDetails().interpreterLanguageNeeds());
                            logger.info("--------------interpreterLanguageNeeds updated---------");
                            apiDefendant.setBailStatus(defendant.personDefendant().bailStatus().description());
                            logger.info("--------------personDefendant updated---------");
                        }

                        if (defendant.legalEntityDefendant() != null) {
                            DefendantOrganisation defendantOrganisation = new DefendantOrganisation();
                            defendantOrganisation.setName(defendant.legalEntityDefendant().organisation().name());
                            apiDefendant.setDefendantOrganisation(defendantOrganisation);
                        }
                    });
        }
        return apiDefendant;
    }
    private DefendantPerson getPersonInfo(uk.gov.moj.cpp.staging.dcs.domain.common.pojo.Defendant defendant) {

        DefendantPerson defendantPerson = new DefendantPerson();
        final PersonDefendant personDefendant = defendant.personDefendant();
        final Person personDetails = personDefendant.personDetails();

        defendantPerson.setForename(personDetails.firstName());
        defendantPerson.setSurname(personDetails.lastName());
        Optional.ofNullable(personDetails.middleName()).ifPresent(defendantPerson::setMiddleName);
        Optional.ofNullable(personDetails.dateOfBirth())
                .ifPresent(localDate -> defendantPerson.setDateOfBirth(localDate.toString()));

        return defendantPerson;
    }

    private void handleSuccess(final UpdateDefendantDetailsRequest updateDefendantDetailsRequest, final String payload) {
        final UUID transactionReference = UUID.fromString(updateDefendantDetailsRequest.getTransactionRef());
        final UUID defendantId = UUID.fromString(updateDefendantDetailsRequest.getDefendant().getId());
        dcsNotificationHelper.saveOrUpdateTransactionMetadata(transactionReference, UUID.fromString(updateDefendantDetailsRequest.getCaseId()), defendantId, TransactionStatus.SUCCESS.toString(), TransactionType.DEFENDANT_UPDATE, null);
        dcsNotificationHelper.saveOrUpdateTransactionDetails(transactionReference, fromString(updateDefendantDetailsRequest.getCaseId()), payload, TransactionStatus.SUCCESS.toString(), null, TransactionType.DEFENDANT_UPDATE);

        final Defendant defendant = updateDefendantDetailsRequest.getDefendant();
        if (nonNull(defendant.getDefendantPerson())) {
            final DefendantPerson defendantPerson = defendant.getDefendantPerson();
            dcsDefendantRepository.updateDefendentDetails(defendantPerson.getForename(), defendantPerson.getMiddleName(), defendantPerson.getSurname(), LocalDate.parse(defendantPerson.getDateOfBirth()), defendantId);
        } else {
            dcsDefendantRepository.updateDefendantOrg(defendant.getDefendantOrganisation().getName(), defendantId);
        }

        logger.info("Defendant updates are successful for defendantId: {}", defendantId);
    }

    private void handleBadRequest(final String messageBody, final String payload, final UpdateDefendantDetailsRequest updateDefendantDetailsRequest) {
        final BadRequestErrorResponsePayload badRequestErrorResponsePayload;
        String errMsg;
        try {
            badRequestErrorResponsePayload = new ObjectMapper().readValue(messageBody, BadRequestErrorResponsePayload.class);
            errMsg = badRequestErrorResponsePayload.getErrorMessage();
        } catch (JsonProcessingException e) {
            errMsg = ERROR_MESSAGE + e.getMessage();
        }

        updateAsFailed(payload, updateDefendantDetailsRequest, errMsg);
    }

    private ExecutionInfo updateStatusAndRetry(final String payload, final UpdateDefendantDetailsRequest updateDefendantDetailsRequest, final String responseErr) {
        final UUID transactionRefId = UUID.fromString(updateDefendantDetailsRequest.getTransactionRef());
        final List<TransactionMetadataEntity> metadataEntities = transactionMetadataRepository.findByTransactionReferenceId(transactionRefId);
        if (metadataEntities.isEmpty()) {
            dcsNotificationHelper.saveOrUpdateTransactionMetadata(transactionRefId, UUID.fromString(updateDefendantDetailsRequest.getCaseId()), UUID.fromString(updateDefendantDetailsRequest.getDefendant().getId()), RETRY.name(), TransactionType.DEFENDANT_UPDATE, null);
        }
        final TransactionDetailEntity transactionDetailEntity = transactionDetailRepository.findByTransactionReferenceId(transactionRefId);
        if (isNull(transactionDetailEntity) || isNull(transactionDetailEntity.getTransactionRefId())) {
            dcsNotificationHelper.saveOrUpdateTransactionDetails(transactionRefId, fromString(updateDefendantDetailsRequest.getCaseId()), payload, RETRY.name(), responseErr, TransactionType.DEFENDANT_UPDATE);
        }

        return getRetryExecutionInfo(new DcsResponseProcessingException(responseErr), updateDefendantDetailsRequest.getTransactionRef(), DEFENDANT_UPDATE_TASK);
    }
    private void handleErrors(final String messageBody, final String payload, final UpdateDefendantDetailsRequest updateDefendantDetailsRequest) {
        final ErrorResponsePayload errorResponsePayload;
        String responseErr;
        try {
            errorResponsePayload = new ObjectMapper().readValue(messageBody, ErrorResponsePayload.class);
            responseErr = format("%s: %s", errorResponsePayload.getErrorCode(), errorResponsePayload.getErrorMessage());
        } catch (JsonProcessingException e) {
            responseErr = ERROR_MESSAGE + e.getMessage();
        }

        updateAsFailed(payload, updateDefendantDetailsRequest, responseErr);
    }

    private void updateAsFailed(final String payload, final UpdateDefendantDetailsRequest updateDefendantDetailsRequest, final String errorMessage) {
        dcsNotificationHelper.saveOrUpdateTransactionMetadata(UUID.fromString(updateDefendantDetailsRequest.getTransactionRef()), UUID.fromString(updateDefendantDetailsRequest.getCaseId()), UUID.fromString(updateDefendantDetailsRequest.getDefendant().getId()), TransactionStatus.FAILED.toString(), TransactionType.DEFENDANT_UPDATE, null);
        dcsNotificationHelper.saveOrUpdateTransactionDetails(UUID.fromString(updateDefendantDetailsRequest.getTransactionRef()), fromString(updateDefendantDetailsRequest.getCaseId()), payload, TransactionStatus.FAILED.toString(), errorMessage, TransactionType.DEFENDANT_UPDATE);

        dcsOperationHelper.unlinkByCaseIdIfErrorsPresent(errorMessage, UUID.fromString(updateDefendantDetailsRequest.getCaseId()));
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}

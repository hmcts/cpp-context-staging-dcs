package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.fromString;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static uk.gov.hmcts.dcs.openapi.model.UpdateDefendantRepresentationRequest.ActionEnum.CREATE;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_URN;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.ERROR_CODE;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.ORGANISATION_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.TRANSACTION_REF;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.RETRY;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.DCS_NOTIFICATION_TASK;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.DEFENCE_REPRESENTATION_TASK;

import uk.gov.hmcts.dcs.openapi.model.Defendant;
import uk.gov.hmcts.dcs.openapi.model.ErrorResponsePayload;
import uk.gov.hmcts.dcs.openapi.model.LinkCaseAndDefendantRequest;
import uk.gov.hmcts.dcs.openapi.model.RequestFulfilledResponsePayload;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.annotation.Task;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.staging.dcs.domain.common.Constants;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.ProsecutionCase;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.jobdata.DcsDefenceUpdateJobData;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsNotificationHelper;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsResponseProcessingException;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.ProsecutionCaseHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsRestNotificationService;
import uk.gov.moj.cpp.staging.dcs.event.service.DefenceService;
import uk.gov.moj.cpp.staging.dcs.event.service.LinkCaseAndDefendantRequestConverter;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsDefendantEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionMetadataEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsDefendantRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings({"squid:S6813","squid:S2629"})
@Task(DCS_NOTIFICATION_TASK)
public class DcsNotificationTask extends BaseTask implements ExecutableTask {
    public static final String CREATE_PAYLOAD = "createPayload";
    public static final String ASSOCIATION = "association";
    public static final String DEFENDANT_ID_LIST = "DEFENDANT_ID_LIST";
    @Inject
    private DcsRestNotificationService dcsRestNotificationService;

    @Inject
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Inject
    private TransactionDetailRepository transactionDetailRepository;
    @Inject
    private TransactionMetadataRepository transactionMetadataRepository;

    @Inject
    private ExecutionService executionService;

    @Inject
    private UtcClock clock;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Inject
    private DcsNotificationHelper dcsNotificationHelper;

    @Inject
    private LinkCaseAndDefendantRequestConverter linkCaseAndDefendantRequestConverter;

    @Inject
    private DefenceService defenceService;

    @Inject
    private DcsOperationHelper dcsOperationHelper;

    @Inject
    private ProsecutionCaseHelper prosecutionCaseHelper;
    @Inject
    private DcsDefendantRepository dcsDefendantRepository;

    @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        return Optional.of(retryConfiguration.getTaskRetryDurationsSeconds());
    }

    @Override
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {
        final JsonObject jobData = executionInfo.getJobData();
        final String transactionRef = jobData.getString(TRANSACTION_REF);
        final String caseUrn = jobData.getString(CASE_URN);
        logger.info("Processing Dcs Notification Task for transactionRef: {}", transactionRef);

        final JsonObject jsonObject = jobData.getJsonObject(CREATE_PAYLOAD);
        final JsonObject metadataObject = jobData.getJsonObject("metadata");
        final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest = linkCaseAndDefendantRequestConverter.convert(jsonObject, transactionRef);
        final String payload = String.valueOf(objectToJsonObjectConverter.convert(linkCaseAndDefendantRequest));

        try {
            logger.info("Invoking the submit API for transactionRef: {}", transactionRef);
            try (Response response = dcsRestNotificationService.submitCaseAndDefendantDetails(caseUrn, payload)) {
                final String messageBody = response.readEntity(String.class);
                logger.info("Response from APIM for transactionRef: {}, {}", transactionRef, messageBody);

                Map<Integer, Runnable> responseHandlers = Map.of(
                        200, () -> handleSuccess(messageBody, payload, linkCaseAndDefendantRequest, metadataObject),
                        201, () -> handleCreated(messageBody, payload, linkCaseAndDefendantRequest, metadataObject),
                        202, () -> handleSuccess(messageBody, payload, linkCaseAndDefendantRequest, metadataObject),
                        204, () -> handleSuccess(messageBody, payload, linkCaseAndDefendantRequest, metadataObject),
                        400, () -> handleBadRequest(messageBody, payload, linkCaseAndDefendantRequest),
                        404, () -> handleErrors(messageBody, payload, linkCaseAndDefendantRequest)
                );

                responseHandlers.getOrDefault(response.getStatus(), () -> handleDefault(messageBody)).run();
            }

        } catch (DcsResponseProcessingException e) {
            return updateStatusAndRetry(payload, linkCaseAndDefendantRequest, e.getMessage());
        } catch (Exception e) {
            logger.error("Exception while sending notification for transactionRef: {}, {}", transactionRef, e.getMessage());
            return updateStatusAndRetry(payload, linkCaseAndDefendantRequest, e.getMessage());
        }

        return executionInfo()
                .withExecutionStatus(COMPLETED)
                .build();
    }

    private void handleSuccess(final String messageBody, final String payload, final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest, JsonObject metadata) {
        if (messageBody == null || messageBody.isBlank()) {
            updateAsFailed(payload, linkCaseAndDefendantRequest, "Empty Response");
        } else {
            handleCreated(messageBody, payload, linkCaseAndDefendantRequest, metadata);
        }
    }

    private void handleCreated(final String messageBody, final String payload, final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest, final JsonObject metadata) {
        final UUID transactionReference = fromString(linkCaseAndDefendantRequest.getTransactionRef());

        final RequestFulfilledResponsePayload requestFulfilledResponsePayload;
        try {
            requestFulfilledResponsePayload = new ObjectMapper().readValue(messageBody, RequestFulfilledResponsePayload.class);
        } catch (JsonProcessingException e) {
            throw new DcsResponseProcessingException("Exception processing the creation response: " + e.getMessage());
        }

        dcsNotificationHelper.updateDcsCaseDetail(linkCaseAndDefendantRequest, requestFulfilledResponsePayload);
        dcsNotificationHelper.saveOrUpdateMetadata(linkCaseAndDefendantRequest, TransactionStatus.SUCCESS.toString());
        dcsNotificationHelper.saveOrUpdateTransactionDetails(transactionReference, fromString(linkCaseAndDefendantRequest.getCaseId()), payload, TransactionStatus.SUCCESS.toString(), null, TransactionType.LINK_DEFENDANT);

        initiateUpdateTasks(linkCaseAndDefendantRequest, metadata, requestFulfilledResponsePayload);
    }

    private void initiateUpdateTasks(final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest, JsonObject metadata, final RequestFulfilledResponsePayload requestFulfilledResponsePayload) {
        logger.info("Initiate update tasks.");

        final ProsecutionCase prosecutionCase = prosecutionCaseHelper.getProsecutionCase(metadata, UUID.fromString(linkCaseAndDefendantRequest.getCaseId()));
        if (nonNull(prosecutionCase)) {
            prosecutionCase.defendants().stream().forEach(defendant -> {
                final UUID defendantId = defendant.id();
                final UUID masterDefendantId = defendant.masterDefendantId();

                final DcsDefendantEntity dcsDefendantEntity = dcsDefendantRepository.findByDefendantId(defendantId);
                if(nonNull(dcsDefendantEntity)){
                    dcsDefendantRepository.updateMasterDefendant(masterDefendantId,defendantId);
                }
            });
        }

        linkCaseAndDefendantRequest.getDefendants().forEach(defendant -> processDefenceUpdate(linkCaseAndDefendantRequest, metadata, defendant));

        initiateCaseMaterialUpdate(linkCaseAndDefendantRequest, requestFulfilledResponsePayload);
    }

    private void initiateCaseMaterialUpdate(final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest, final RequestFulfilledResponsePayload requestFulfilledResponsePayload) {

        logger.info("starting case material update on completion of link case with caseId {}.", linkCaseAndDefendantRequest.getCaseId());
        final Map<String, String> defendantIdRefereralIdMap = new HashMap<>();
        final MaterialTaskData taskData = new MaterialTaskData();

        taskData.setCaseId(linkCaseAndDefendantRequest.getCaseId());
        taskData.setCaseReferralId(requestFulfilledResponsePayload.getCaseReferral());

        linkCaseAndDefendantRequest.getDefendants()
                .forEach(defendant -> requestFulfilledResponsePayload.getDefendants()
                        .forEach(responseDefendant -> {
                            if (responseDefendant.getDefendantId().equalsIgnoreCase(defendant.getId())) {
                                defendantIdRefereralIdMap.put(defendant.getId(), responseDefendant.getDefendantReferral());
                            }
                        }));
        taskData.setDefendantIdReferralIdMap(defendantIdRefereralIdMap);

        dcsOperationHelper.initiateMaterialTaskForCase(objectToJsonObjectConverter.convert(taskData));
    }

    private void processDefenceUpdate(final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest, final JsonObject metadata, final Defendant defendant) {
        final Optional<JsonObject> associatedOrganisation = defenceService.getAssociatedOrganisation(metadataFrom(metadata).build(), defendant.getId());
        logger.info("Associated organisation for {} defendant Id: {}", defendant.getId(), associatedOrganisation);
        if(associatedOrganisation.isPresent()) {
            final JsonObject association = associatedOrganisation.get().getJsonObject(ASSOCIATION);
            if(!association.isEmpty()) {
                final String organisationId = association.getString(ORGANISATION_ID);
                final DcsDefenceUpdateJobData dcsUpdateJobData = new DcsDefenceUpdateJobData(linkCaseAndDefendantRequest.getCaseId(), defendant.getId(), organisationId, metadata, CREATE.getValue(), UUID.randomUUID().toString());
                final JsonObject defenceRepresentationObject = objectToJsonObjectConverter.convert(dcsUpdateJobData);
                final ExecutionInfo defenceRepresentationExecutionInfo = new ExecutionInfo(
                        defenceRepresentationObject,
                        DEFENCE_REPRESENTATION_TASK,
                        clock.now(),
                        STARTED,
                        Priority.MEDIUM);

                executionService.executeWith(defenceRepresentationExecutionInfo);
            }
        }
    }

    private void handleBadRequest(final String messageBody, final String payload, final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest) {
        String errorMsg = null;
        String errorCode;
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readTree(messageBody);
            if (rootNode.hasNonNull(ERROR_CODE)) {
                errorCode = rootNode.get(ERROR_CODE).asText();
            }
            if (rootNode.hasNonNull(Constants.ERROR_MESSAGE)) {
                errorMsg = rootNode.get(Constants.ERROR_MESSAGE).asText();
            }
        } catch (JsonProcessingException e) {
            errorMsg = ERROR_MESSAGE + e.getMessage();
        }

        updateAsFailed(payload, linkCaseAndDefendantRequest, errorMsg);
    }

    private ExecutionInfo updateStatusAndRetry(final String payload, final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest, final String responseErr) {
        final UUID transactionRefId = fromString(linkCaseAndDefendantRequest.getTransactionRef());
        final List<TransactionMetadataEntity> metadataEntities = transactionMetadataRepository.findByTransactionReferenceId(transactionRefId);
        if (isEmpty(metadataEntities)) {
            dcsNotificationHelper.saveOrUpdateMetadata(linkCaseAndDefendantRequest, RETRY.toString());
        }
        final TransactionDetailEntity transactionDetailEntity = transactionDetailRepository.findByTransactionReferenceId(transactionRefId);
        if (isNull(transactionDetailEntity) || isNull(transactionDetailEntity.getTransactionRefId())) {
            dcsNotificationHelper.saveOrUpdateTransactionDetails(transactionRefId, fromString(linkCaseAndDefendantRequest.getCaseId()), payload, RETRY.toString(), responseErr, TransactionType.LINK_DEFENDANT);
        }

        return getRetryExecutionInfo(new DcsResponseProcessingException(responseErr), linkCaseAndDefendantRequest.getTransactionRef(), DCS_NOTIFICATION_TASK);
    }

    private void updateAsFailed(final String payload, final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest, final String errorMessage) {
        linkCaseAndDefendantRequest.getDefendants().stream()
                .forEach(defendant -> {
                    final DcsCaseDetailEntity dcsCaseDetailEntity = dcsCaseDetailRepository.findByCaseIdDefendantId(fromString(linkCaseAndDefendantRequest.getCaseId()), fromString(defendant.getId()));
                    dcsCaseDetailRepository.updateStatusById(DcsDefendantStatus.FAILED.toString(), dcsCaseDetailEntity.getId());
                });

        dcsNotificationHelper.saveOrUpdateMetadata(linkCaseAndDefendantRequest, TransactionStatus.FAILED.toString());
        dcsNotificationHelper.saveOrUpdateTransactionDetails(fromString(linkCaseAndDefendantRequest.getTransactionRef()), fromString(linkCaseAndDefendantRequest.getCaseId()), payload, TransactionStatus.FAILED.toString(), errorMessage, TransactionType.LINK_DEFENDANT);
        dcsOperationHelper.unlinkByCaseIdIfErrorsPresent(errorMessage, fromString(linkCaseAndDefendantRequest.getCaseId()));
    }

    private void handleErrors(final String messageBody, final String payload, final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest) {
        final ErrorResponsePayload errorResponsePayload;
        String responseErr;
        try {
            errorResponsePayload = new ObjectMapper().readValue(messageBody, ErrorResponsePayload.class);
            responseErr = format("%s: %s", errorResponsePayload.getErrorCode(), errorResponsePayload.getErrorMessage());
        } catch (JsonProcessingException e) {
            responseErr = ERROR_MESSAGE + e.getMessage();
        }

        updateAsFailed(payload, linkCaseAndDefendantRequest, responseErr);
    }
}

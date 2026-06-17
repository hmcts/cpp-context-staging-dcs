package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.UUID.fromString;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.MapUtils.isNotEmpty;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_REFERRAL;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DEFENDANTS;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DEFENDANT_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DEFENDANT_REFERRAL;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DOCUMENT_DATE;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DOCUMENT_NAME;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DOCUMENT_SECTION;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.MATERIAL_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.MATERIAL_URL;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.TRANSACTION_REF;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.UPLOADED_BY_USER_KEY;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.UPLOADED_BY_USER_VALUE;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.RETRY;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.SEND_MATERIAL_TO_DCS_TASK;

import uk.gov.hmcts.dcs.openapi.model.BadRequestErrorResponsePayload;
import uk.gov.hmcts.dcs.openapi.model.ErrorResponsePayload;
import uk.gov.moj.cpp.jobstore.api.annotation.Task;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsNotificationHelper;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsResponseProcessingException;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsRestNotificationService;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionMetadataEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import uk.gov.justice.services.messaging.JsonObjects;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
@SuppressWarnings({"java:S1142", "java:S1309", "java:S2629"})
@Task(SEND_MATERIAL_TO_DCS_TASK)
public class SendMaterialToDcsTask extends BaseTask implements ExecutableTask {

    @Inject
    private DcsOperationHelper dcsOperationHelper;

    @Inject
    private DcsNotificationHelper dcsNotificationHelper;

    @Inject
    private DcsRestNotificationService dcsRestNotificationService;

    @Inject
    private TransactionMetadataRepository transactionMetadataRepository;

    @Inject
    private TransactionDetailRepository transactionDetailRepository;
    @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        return Optional.of(retryConfiguration.getTaskRetryDurationsSeconds());
    }

    @Override
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {

        final JsonObject jobData = executionInfo.getJobData();
        final MaterialTaskData taskData = jsonObjectToObjectConverter.convert(jobData, MaterialTaskData.class);
        final String caseId = taskData.getCaseId();
        final String materialId = taskData.getMaterialId();
        final String payload = buildDcsPayload(taskData).toString();

        try {
            logger.info("Invoking the material API of DCS for caseId {} materialId {}", caseId, materialId);
            try (Response response = dcsRestNotificationService.submitMaterial(taskData.getCaseUrn(), payload)) {
                String responseBody = null;
                if (response.hasEntity()) {
                    responseBody = response.readEntity(String.class);
                }
                final String messageBody = responseBody;
                Map<Integer, Runnable> responseHandlers = Map.of(
                        200, () -> handleSuccess(payload, taskData),
                        201, () -> handleSuccess(payload, taskData),
                        202, () -> handleSuccess(payload, taskData),
                        204, () -> handleSuccess(payload, taskData),
                        400, () -> handleBadRequest(messageBody, payload, taskData),
                        404, () -> handleErrors(messageBody, payload, taskData)
                );

                responseHandlers.getOrDefault(response.getStatus(), () -> handleDefault(messageBody)).run();
            }
        } catch (DcsResponseProcessingException e) {
            return updateStatusAndRetry(payload, taskData, e.getMessage());
        } catch (Exception e) {
            logger.debug("Exception while sending notification : {}", e.getMessage());
            return updateStatusAndRetry(payload, taskData, e.getMessage());
        }

        return dcsOperationHelper.returnCompletedExecutionInfo();
    }

    private void handleBadRequest(final String messageBody, final String payload, final MaterialTaskData materialTaskData) {
        final BadRequestErrorResponsePayload badRequestErrorResponsePayload;
        String errMsg;
        try {
            badRequestErrorResponsePayload = new ObjectMapper().readValue(messageBody, BadRequestErrorResponsePayload.class);
            errMsg = badRequestErrorResponsePayload.getErrorMessage();
        } catch (JsonProcessingException e) {
            errMsg = ERROR_MESSAGE + e.getMessage();
        }

        updateAsFailed(payload, materialTaskData, errMsg);
    }

    private void handleSuccess(final String payload, final MaterialTaskData materialTaskData) {
        dcsNotificationHelper.saveOrUpdateMaterialMetadata(materialTaskData, TransactionStatus.SENT.toString());
        dcsNotificationHelper.saveOrUpdateTransactionDetails(fromString(materialTaskData.getTranRefId()), fromString(materialTaskData.getCaseId()), payload, TransactionStatus.SENT.toString(), null, TransactionType.MATERIAL_UPDATE);
    }

    private void updateAsFailed(final String payload, final MaterialTaskData materialTaskData, final String errorMessage) {
        dcsNotificationHelper.saveOrUpdateMaterialMetadata(materialTaskData, TransactionStatus.FAILED.toString());
        dcsNotificationHelper.saveOrUpdateTransactionDetails(fromString(materialTaskData.getTranRefId()), fromString(materialTaskData.getCaseId()), payload, TransactionStatus.FAILED.toString(), errorMessage, TransactionType.MATERIAL_UPDATE);

        dcsOperationHelper.unlinkByCaseIdIfErrorsPresent(errorMessage, UUID.fromString(materialTaskData.getCaseId()));
    }

    private JsonObject buildDcsPayload(final MaterialTaskData taskData) {
        final JsonObjectBuilder objectBuilder = JsonObjects.createObjectBuilder()
                .add(CASE_ID, taskData.getCaseId())
                .add(CASE_REFERRAL, taskData.getCaseReferralId())
                .add(MATERIAL_ID, taskData.getMaterialId())
                .add(TRANSACTION_REF, taskData.getTranRefId())
                .add(MATERIAL_URL, taskData.getAzureStorageUrl())
                .add(DOCUMENT_NAME, taskData.getDocumentName())
                .add(DOCUMENT_DATE, taskData.getDocumentDate())
                .add(UPLOADED_BY_USER_KEY, UPLOADED_BY_USER_VALUE)
                .add(DOCUMENT_SECTION, taskData.getDocumentSection());

        if (isNotEmpty(taskData.getDefendantIdReferralIdMap())) {
            final JsonArrayBuilder defendantArray = JsonObjects.createArrayBuilder();
            taskData.getDefendantIdReferralIdMap().forEach((k, v) ->
                    defendantArray.add(JsonObjects.createObjectBuilder()
                            .add(DEFENDANT_ID, k.toString())
                            .add(DEFENDANT_REFERRAL, v.toString())
                            .build())
            );

            objectBuilder.add(DEFENDANTS, defendantArray.build());
        }

        return objectBuilder.build();
    }

    private void handleErrors(final String messageBody, final String payload, final MaterialTaskData materialTaskData) {
        final ErrorResponsePayload errorResponsePayload;
        String responseErr;
        try {
            errorResponsePayload = new ObjectMapper().readValue(messageBody, ErrorResponsePayload.class);
            responseErr = format("%s: %s", errorResponsePayload.getErrorCode(), errorResponsePayload.getErrorMessage());
        } catch (JsonProcessingException e) {
            responseErr = ERROR_MESSAGE + e.getMessage();
        }

        updateAsFailed(payload, materialTaskData, responseErr);
    }

    private ExecutionInfo updateStatusAndRetry(final String payload, final MaterialTaskData materialTaskData, final String responseErr) {
        final UUID transactionRefId = UUID.fromString(materialTaskData.getTranRefId());
        final List<TransactionMetadataEntity> metadataEntities = transactionMetadataRepository.findByTransactionReferenceId(transactionRefId);
        if (isEmpty(metadataEntities)) {
            dcsNotificationHelper.saveOrUpdateMaterialMetadata(materialTaskData, RETRY.name());
        }
        final TransactionDetailEntity transactionDetailEntity = transactionDetailRepository.findByTransactionReferenceId(transactionRefId);
        if (isNull(transactionDetailEntity) || isNull(transactionDetailEntity.getTransactionRefId())) {
            dcsNotificationHelper.saveOrUpdateTransactionDetails(transactionRefId, fromString(materialTaskData.getCaseId()), payload, RETRY.name(), responseErr, TransactionType.MATERIAL_UPDATE);
        }

        return getRetryExecutionInfo(new DcsResponseProcessingException(responseErr), transactionRefId.toString(), SEND_MATERIAL_TO_DCS_TASK);
    }
}

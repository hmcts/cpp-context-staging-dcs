package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.UUID.fromString;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.ACTION;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DEFENDANT_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.METADATA;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.ORGANISATION_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.TRANSACTION_REF;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.RETRY;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.DEFENCE_REPRESENTATION_TASK;

import uk.gov.hmcts.dcs.openapi.model.BadRequestErrorResponsePayload;
import uk.gov.hmcts.dcs.openapi.model.DefenceRepresentation;
import uk.gov.hmcts.dcs.openapi.model.ErrorResponsePayload;
import uk.gov.hmcts.dcs.openapi.model.UpdateDefendantRepresentationRequest;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.moj.cpp.jobstore.api.annotation.Task;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.DefenceOrganisation;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsNotificationHelper;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsResponseProcessingException;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsRestNotificationService;
import uk.gov.moj.cpp.staging.dcs.event.service.UserGroupService;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionMetadataEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsDefendantRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

@Task(DEFENCE_REPRESENTATION_TASK)
public class DefenceRepresentationTask extends BaseTask implements ExecutableTask {

    public static final String ERROR_MESSAGE = "Exception processing the bad request response: ";
    @Inject
    private DcsCaseDetailRepository dcsCaseDetailRepository;
    @Inject
    private UserGroupService userGroupService;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Inject
    private DcsRestNotificationService dcsRestNotificationService;

    @Inject
    private DcsNotificationHelper dcsNotificationHelper;
    @Inject
    private TransactionDetailRepository transactionDetailRepository;
    @Inject
    private TransactionMetadataRepository transactionMetadataRepository;
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

    try {
        final JsonObject jobData = executionInfo.getJobData();
        final String caseId = jobData.getString(CASE_ID);
        final String defendantId = jobData.getString(DEFENDANT_ID);
        final String organisationId = jobData.getString(ORGANISATION_ID);
        final JsonObject metadataObject = jobData.getJsonObject(METADATA);
        final String action = jobData.getString(ACTION);
        final String transactionRef = jobData.getString(TRANSACTION_REF);

        final DefenceOrganisation organisation = getOrganisationDetails(metadataObject, organisationId);

        UpdateDefendantRepresentationRequest updateDefendantRepresentationRequest
                = new UpdateDefendantRepresentationRequest();
        updateDefendantRepresentationRequest.setTransactionRef(transactionRef);
        updateDefendantRepresentationRequest.setCaseId(caseId);
        updateDefendantRepresentationRequest.setAction(UpdateDefendantRepresentationRequest.ActionEnum.fromValue(action));

        DefenceRepresentation defenceRepresentation = new DefenceRepresentation();
        defenceRepresentation.setOrganisationName(organisation.organisationName());
        defenceRepresentation.setEmail(organisation.email());
        updateDefendantRepresentationRequest.defenceRepresentation(defenceRepresentation);

        final DcsCaseDetailEntity dcsCaseDetailEntity = dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId), UUID.fromString(defendantId));
        String caseUrn = dcsCaseDetailEntity.getCaseUrn();
        String defendantReferral = String.valueOf(dcsCaseDetailEntity.getDefendantRefId());
        String caseReferral = String.valueOf(dcsCaseDetailEntity.getCaseRefId());
        updateDefendantRepresentationRequest.setCaseReferral(caseReferral);

        String payload = String.valueOf(objectToJsonObjectConverter.convert(updateDefendantRepresentationRequest));

        try (Response response = dcsRestNotificationService.sendUpdatedDefenceRepresentationDetails(caseUrn, defendantReferral, payload)) {

            final String messageBody = response.readEntity(String.class);
            logger.info("Response from APIM for caseId: {}, {}", caseId, messageBody);
            final String finalPayload = payload;
            Map<Integer, Runnable> responseHandlers = Map.of(
                    204, () -> handleSuccess(updateDefendantRepresentationRequest, defendantId, finalPayload),
                    400, () -> handleBadRequest(messageBody, finalPayload, defendantId, updateDefendantRepresentationRequest),
                    404, () -> handleErrors(messageBody, payload, defendantId, updateDefendantRepresentationRequest)
            );

            responseHandlers.getOrDefault(response.getStatus(), () -> handleDefault(messageBody)).run();
        } catch (DcsResponseProcessingException e) {
            return updateStatusAndRetry(payload, updateDefendantRepresentationRequest, defendantId, e.getMessage());
        } catch (Exception e) {
            logger.error("Exception while sending defence representation for caseId: {}, {}", caseId, e.getMessage());
            return updateStatusAndRetry(payload, updateDefendantRepresentationRequest, defendantId, e.getMessage());
        }
    }
    catch (Exception exception) {
        logger.error("Exception while sending defence representation from: {}", exception.getMessage());
    }
        return executionInfo()
                .withExecutionStatus(COMPLETED)
                .build();
    }

    private void handleSuccess(final UpdateDefendantRepresentationRequest updateDefendantRepresentationRequest, final String defendantId, final String payload) {
        final UUID transactionReference = UUID.fromString(updateDefendantRepresentationRequest.getTransactionRef());
        dcsNotificationHelper.saveOrUpdateTransactionMetadata(transactionReference, UUID.fromString(updateDefendantRepresentationRequest.getCaseId()), UUID.fromString(defendantId), TransactionStatus.SUCCESS.toString(), TransactionType.DEFENCE_REPRESENTATION, null);
        dcsNotificationHelper.saveOrUpdateTransactionDetails(transactionReference, fromString(updateDefendantRepresentationRequest.getCaseId()), payload, TransactionStatus.SUCCESS.toString(), null, TransactionType.DEFENCE_REPRESENTATION);

        final Set<String> updateActions = Set.of("CREATE", "UPDATE");
        final String action = updateDefendantRepresentationRequest.getAction().getValue();

        final DefenceRepresentation defenceRepresentation = updateDefendantRepresentationRequest.getDefenceRepresentation();
        if (updateActions.contains(action)) {
            dcsDefendantRepository.updateDefenceRepresentationDetails(defenceRepresentation.getOrganisationName(),
                    Objects.requireNonNullElse(defenceRepresentation.getEmail(), ""), UUID.fromString(defendantId));
        } else {
            dcsDefendantRepository.updateDefenceRepresentationDetails(StringUtils.EMPTY, StringUtils.EMPTY, UUID.fromString(defendantId));
        }

        logger.info("Defendant updates are successful for defendantId: {}", defendantId);
    }

    private void handleBadRequest(final String messageBody, final String payload, final String defendantId, final UpdateDefendantRepresentationRequest updateDefendantRepresentationRequest) {
        final BadRequestErrorResponsePayload badRequestErrorResponsePayload;
        try {
            badRequestErrorResponsePayload = new ObjectMapper().readValue(messageBody, BadRequestErrorResponsePayload.class);
        } catch (JsonProcessingException e) {
            throw new DcsResponseProcessingException(ERROR_MESSAGE + e.getMessage());
        }

        updateAsFailed(payload, updateDefendantRepresentationRequest, defendantId, badRequestErrorResponsePayload.getErrorMessage());
    }

    private ExecutionInfo updateStatusAndRetry(final String payload, final UpdateDefendantRepresentationRequest updateDefendantRepresentationRequest, final String defendantId, final String responseErr) {
        final UUID transactionRefId = UUID.fromString(updateDefendantRepresentationRequest.getTransactionRef());
        final List<TransactionMetadataEntity> metadataEntities = transactionMetadataRepository.findByTransactionReferenceId(transactionRefId);
        if (metadataEntities.isEmpty()) {
            dcsNotificationHelper.saveOrUpdateTransactionMetadata(transactionRefId, UUID.fromString(updateDefendantRepresentationRequest.getCaseId()), UUID.fromString(defendantId), RETRY.toString(), TransactionType.DEFENCE_REPRESENTATION, null);
        }
        final TransactionDetailEntity transactionDetailEntity = transactionDetailRepository.findByTransactionReferenceId(transactionRefId);
        if (isNull(transactionDetailEntity.getTransactionRefId())) {
            dcsNotificationHelper.saveOrUpdateTransactionDetails(transactionRefId, fromString(updateDefendantRepresentationRequest.getCaseId()), payload, RETRY.toString(), responseErr, TransactionType.DEFENCE_REPRESENTATION);
        }

        return getRetryExecutionInfo(new DcsResponseProcessingException(responseErr), updateDefendantRepresentationRequest.getTransactionRef(), DEFENCE_REPRESENTATION_TASK);
    }

    private void updateAsFailed(final String payload, final UpdateDefendantRepresentationRequest updateDefendantRepresentationRequest, final String defendantId, final String errorMessage) {
        dcsNotificationHelper.saveOrUpdateTransactionMetadata(UUID.fromString(updateDefendantRepresentationRequest.getTransactionRef()), UUID.fromString(updateDefendantRepresentationRequest.getCaseId()), UUID.fromString(defendantId), DcsDefendantStatus.FAILED.toString(), TransactionType.DEFENCE_REPRESENTATION, null);
        dcsNotificationHelper.saveOrUpdateTransactionDetails(UUID.fromString(updateDefendantRepresentationRequest.getTransactionRef()), fromString(updateDefendantRepresentationRequest.getCaseId()), payload, TransactionStatus.FAILED.toString(), errorMessage, TransactionType.DEFENCE_REPRESENTATION);

        dcsOperationHelper.unlinkByCaseIdIfErrorsPresent(errorMessage, UUID.fromString(updateDefendantRepresentationRequest.getCaseId()));
    }

    private void handleErrors(final String messageBody, final String payload, final String defendantId, final UpdateDefendantRepresentationRequest updateDefendantRepresentationRequest) {
        final ErrorResponsePayload errorResponsePayload;
        String responseErr;
        try {
            errorResponsePayload = new ObjectMapper().readValue(messageBody, ErrorResponsePayload.class);
            responseErr = format("%s: %s", errorResponsePayload.getErrorCode(), errorResponsePayload.getErrorMessage());
        } catch (JsonProcessingException e) {
            responseErr = ERROR_MESSAGE + e.getMessage();
        }

        updateAsFailed(payload, updateDefendantRepresentationRequest, defendantId, responseErr);
    }

    private DefenceOrganisation getOrganisationDetails(final JsonObject metadata, final String organisationId) {
        final JsonObject organisationDetails = userGroupService.getOrganisationDetails(metadataFrom(metadata).build(), organisationId);
        return jsonObjectToObjectConverter.convert(organisationDetails, DefenceOrganisation.class);
    }
}

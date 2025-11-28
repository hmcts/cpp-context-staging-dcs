package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import uk.gov.hmcts.dcs.openapi.model.ErrorResponsePayload;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsResponseProcessingException;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.RetryConfiguration;

import java.util.UUID;

import javax.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

public abstract class BaseTask {
    public static final String ERROR_MESSAGE = "Exception processing the bad request response: ";
    @Inject
    public JsonObjectToObjectConverter jsonObjectToObjectConverter;
    @Inject
    public Logger logger;
    @Inject
    public RetryConfiguration retryConfiguration;
    @Inject
    public SetFailedStatusTaskFactory setFailedStatusTaskFactory;

    public ExecutionInfo getRetryExecutionInfo(final Exception e, final String transactionReference, final String taskName) {
        final String errorMessage = String.format("Error executing %s for transactionReference %s: %s", taskName, transactionReference, e.getMessage());
        logger.warn(errorMessage);
        return setFailedStatusTaskFactory.createRetryWithSetNotificationStatusFailedTaskOnExhaust(UUID.fromString(transactionReference), taskName, errorMessage);
    }

    public ExecutionInfo handleDefault(final String messageBody) {
        final ErrorResponsePayload errorResponsePayload = parseErrorPayload(messageBody);
        final String responseErr = String.format("%s: %s", errorResponsePayload.getErrorCode(), errorResponsePayload.getErrorMessage());
        throw new DcsResponseProcessingException(responseErr);
    }
    public ErrorResponsePayload parseErrorPayload(String messageBody) {
        try {
            return new ObjectMapper().readValue(messageBody, ErrorResponsePayload.class);
        } catch (JsonProcessingException e) {
            throw new DcsResponseProcessingException(errorExtractor(messageBody));
        } catch (DcsResponseProcessingException dcsResponseProcessingException){
            throw new DcsResponseProcessingException(dcsResponseProcessingException.getMessage());
        }
    }

    private String errorExtractor(String messageBody){
        try {
            final String errorCode = new ObjectMapper().readTree(messageBody).get("errorCode").asText();
            final String errorMsg = new ObjectMapper().readTree(messageBody).get("errorMessage").asText();
            return String.format("%s: %s", errorCode, errorMsg);
        } catch (JsonProcessingException e) {
            throw new DcsResponseProcessingException(e.getMessage());
        }
    }
}

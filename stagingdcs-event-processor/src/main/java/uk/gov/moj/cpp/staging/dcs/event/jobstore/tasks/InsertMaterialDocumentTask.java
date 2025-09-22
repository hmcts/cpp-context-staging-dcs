package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.lang.String.format;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.INSERT_MATERIAL_DOCUMENT_TASK;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.moj.cpp.jobstore.api.annotation.Task;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsResponseProcessingException;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.json.JsonObject;

@SuppressWarnings({"squid:S6813", "squid:S2629"})
@Task(INSERT_MATERIAL_DOCUMENT_TASK)
public class InsertMaterialDocumentTask extends BaseTask implements ExecutableTask {

    @Inject
    private DcsOperationHelper dcsOperationHelper;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;


    @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        return Optional.of(retryConfiguration.getTaskRetryDurationsSeconds());
    }

    @Override
    @SuppressWarnings("java:S1142")
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {
        final JsonObject jobData = executionInfo.getJobData();
        final MaterialTaskData taskData = jsonObjectToObjectConverter.convert(jobData, MaterialTaskData.class);
        boolean shouldProcessNextTask = true;

        try {
            logger.info("Invoking INSERT_MATERIAL_DOCUMENT_TASK for caseId {} and materialId {}", taskData.getCaseId(), taskData.getMaterialId());
            try {
                dcsOperationHelper.insertDocumentData(taskData);
            } catch (Exception exception) {
                logger.error(format("Completing INSERT_MATERIAL_DOCUMENT_TASK for caseId %s and materialId %s due to already present in the document tables", taskData.getCaseId(), taskData.getMaterialId()));
                shouldProcessNextTask = false;
            }

            if(shouldProcessNextTask){
                dcsOperationHelper.processCheckMaterialStatus(objectToJsonObjectConverter.convert(taskData));
            }
        } catch (Exception e) {
            logger.error(format("Exception while processing INSERT_MATERIAL_DOCUMENT_TASK task : %s", e.getMessage()));
            return retryTask(taskData, e.getMessage());
        }

        return dcsOperationHelper.returnCompletedExecutionInfo();
    }

    private ExecutionInfo retryTask(final MaterialTaskData materialTaskData, final String responseErr) {
        return getRetryExecutionInfo(new DcsResponseProcessingException(responseErr), materialTaskData.getTranRefId(), INSERT_MATERIAL_DOCUMENT_TASK);
    }
}

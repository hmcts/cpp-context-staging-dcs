package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.lang.String.format;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.SEND_MATERIAL_TO_DCS_TASK;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.UPLOAD_MATERIAL_TO_STORAGE_TASK;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.annotation.Task;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsResponseProcessingException;
import uk.gov.moj.cpp.staging.dcs.event.service.AzureStorageService;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.MaterialService;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.inject.Inject;
import javax.json.JsonObject;

@SuppressWarnings({"squid:S6813", "squid:S2629"})
@Task(UPLOAD_MATERIAL_TO_STORAGE_TASK)
public class UploadMaterialToStorageTask extends BaseTask implements ExecutableTask {

    @Inject
    private ExecutionService executionService;

    @Inject
    private UtcClock clock;

    @Inject
    private DcsOperationHelper dcsOperationHelper;

    @Inject
    private AzureStorageService azureStorageService;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Inject
    private MaterialService materialService;


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
        try {
            logger.info("Invoking UPLOAD_MATERIAL_TO_STORAGE_TASK for caseId {} and materialId {}", caseId, materialId);
            final Optional<String> azureMaterialUrl = materialService.queryMaterialWithMaterialIdToGetAzureBlobUrlOfMaterial(materialId);
            if (azureMaterialUrl.isEmpty()) {
                throw new NoSuchElementException(format("Material Url is not returned from material for materialId %s", materialId));
            }
            String azureUrl = azureStorageService.storeMaterialToAzureStorage(taskData.getDocumentName(), taskData.getTranRefId(), azureMaterialUrl.get());
            taskData.setAzureStorageUrl(azureUrl);
            initiateSubmitMaterialTask(objectToJsonObjectConverter.convert(taskData));
        } catch (Exception e) {
            logger.error("Exception while executing UPLOAD_MATERIAL_TO_STORAGE_TASK task with : {}", e.getMessage());
            return retryTask(taskData, e.getMessage());
        }

        return dcsOperationHelper.returnCompletedExecutionInfo();
    }

    private void initiateSubmitMaterialTask(final JsonObject inputJsonObject) {
        logger.info("Initiating initiateSubmitMaterialTask from UPLOAD_MATERIAL_TO_STORAGE_TASK..");

        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                inputJsonObject,
                SEND_MATERIAL_TO_DCS_TASK,
                clock.now(),
                STARTED,
                Priority.MEDIUM);

        executionService.executeWith(materialExecutionInfo);
    }

    private ExecutionInfo retryTask(final MaterialTaskData materialTaskData, final String responseErr) {
        return getRetryExecutionInfo(new DcsResponseProcessingException(responseErr), materialTaskData.getTranRefId(), UPLOAD_MATERIAL_TO_STORAGE_TASK);
    }
}

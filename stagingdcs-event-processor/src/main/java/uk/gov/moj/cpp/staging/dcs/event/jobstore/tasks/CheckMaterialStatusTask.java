package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.util.Arrays.asList;
import static java.util.Objects.isNull;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.CHECK_MATERIAL_STATUS_TASK;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.moj.cpp.jobstore.api.annotation.Task;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsResponseProcessingException;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.MaterialService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.json.JsonObject;

@SuppressWarnings({"squid:S6813","squid:S2629"})
@Task(CHECK_MATERIAL_STATUS_TASK)
public class CheckMaterialStatusTask extends BaseTask implements ExecutableTask {

    @Inject
    private MaterialService materialService;

    @Inject
    private DcsOperationHelper dcsOperationHelper;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;


    @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        return Optional.of(retryConfiguration.getRecheckMaterialStatusTaskRetryDurationsSeconds());
    }

    @Override
    @SuppressWarnings("java:S1142")
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {
        final JsonObject jobData = executionInfo.getJobData();
        final MaterialTaskData taskData = jsonObjectToObjectConverter.convert(jobData, MaterialTaskData.class);

        try {
            logger.info("Invoking CHECK_MATERIAL_STATUS_TASK for caseId {} and materialId {}", taskData.getCaseId(), taskData.getMaterialId());
            final String materialId = taskData.getMaterialId();
            final Map<String, Boolean> downloadableStatusMap = materialService.getDownloadableMaterialMap(asList(materialId));
            if (isMaterialNotDownloadable(taskData, downloadableStatusMap)) {
                return retryTask(taskData, "material is not ready to download hence retrying the task");
            }
            dcsOperationHelper.initiateUploadToStorageTask(objectToJsonObjectConverter.convert(taskData));
        } catch (Exception e) {
            logger.error("Exception while processing CHECK_MATERIAL_STATUS_TASK, caseId: {}, materialId:{}, error: {}", taskData.getCaseId(), taskData.getMaterialId(), e.getMessage());
            return retryTask(taskData, e.getMessage());
        }

        return dcsOperationHelper.returnCompletedExecutionInfo();
    }

    private static boolean isMaterialNotDownloadable(final MaterialTaskData taskData, final Map<String, Boolean> downloadableStatusMap) {
        return isNull(downloadableStatusMap.get(taskData.getMaterialId())) || !downloadableStatusMap.get(taskData.getMaterialId());
    }

    private ExecutionInfo retryTask(final MaterialTaskData materialTaskData, final String responseErr) {
        return getRetryExecutionInfo(new DcsResponseProcessingException(responseErr), materialTaskData.getTranRefId(), CHECK_MATERIAL_STATUS_TASK);
    }
}

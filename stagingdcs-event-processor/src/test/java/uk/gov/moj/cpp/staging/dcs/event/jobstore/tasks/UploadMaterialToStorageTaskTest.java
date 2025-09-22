package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.SEND_MATERIAL_TO_DCS_TASK;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.UPLOAD_MATERIAL_TO_STORAGE_TASK;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.event.service.AzureStorageService;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.MaterialService;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class UploadMaterialToStorageTaskTest {

    @InjectMocks
    private UploadMaterialToStorageTask uploadMaterialToStorageTask;
    @Mock
    DcsOperationHelper dcsOperationHelper;
    @Spy
    private JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(new ObjectMapperProducer().objectMapper());
    @Spy
    private ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(new ObjectMapperProducer().objectMapper());
    @Mock
    private Logger logger;
    @Mock
    AzureStorageService azureStorageService;
    @Mock
    private SetFailedStatusTaskFactory setFailedStatusTaskFactory;
    @Mock
    private ExecutionService executionService;
    @Mock
    UtcClock utcClock;
    @Mock
    MaterialService materialService;

    @Test
    void executeMethodShouldHandlePostSuccess() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId = randomUUID().toString();
        final String tranRefId = randomUUID().toString();
        final String documentTypeId = randomUUID().toString();
        final MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), tranRefId, materialId, documentTypeId);

        when(dcsOperationHelper.returnCompletedExecutionInfo()).thenReturn(returnCompletedExecutionInfo());
        when(azureStorageService.storeMaterialToAzureStorage(any(), any(), any())).thenReturn("https//storageUrlForDcs/azure/dcs?material=".concat(materialId));
        when(materialService.queryMaterialWithMaterialIdToGetAzureBlobUrlOfMaterial(any())).thenReturn(Optional.of("https//storageUrlFromMaterial/azure/dcs?material=".concat(materialId)));
        when(utcClock.now()).thenReturn(ZonedDateTime.now());

        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                UPLOAD_MATERIAL_TO_STORAGE_TASK,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        uploadMaterialToStorageTask.execute(materialExecutionInfo);

        final ArgumentCaptor<ExecutionInfo> submitMaterialTask = ArgumentCaptor.forClass(ExecutionInfo.class);
        verify(executionService, times(1)).executeWith(submitMaterialTask.capture());
        final ExecutionInfo info = submitMaterialTask.getValue();
        assertThat(info.getNextTask(), is(SEND_MATERIAL_TO_DCS_TASK));
        final MaterialTaskData storageData = jsonObjectToObjectConverter.convert(info.getJobData(), MaterialTaskData.class);
        assertOnMaterialTasData(storageData, caseId, materialId, defendantId);
        assertThat(storageData.getAzureStorageUrl(), containsString(materialId));
        assertThat(storageData.getTranRefId(), is(tranRefId));

        verify(dcsOperationHelper, times(1)).returnCompletedExecutionInfo();
    }

    @Test
    void executeMethodShouldRetryTask() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId = "NonUUIDStringForExceptionGeneration";
        final String tranRefId = randomUUID().toString();
        final String documentTypeId = randomUUID().toString();
        final MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), tranRefId, materialId, documentTypeId);

        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                UPLOAD_MATERIAL_TO_STORAGE_TASK,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        uploadMaterialToStorageTask.execute(materialExecutionInfo);

        verify(executionService, times(0)).executeWith(any());
        verify(dcsOperationHelper, times(0)).returnCompletedExecutionInfo();
        final ArgumentCaptor<String> taskName = ArgumentCaptor.forClass(String.class);
        verify(setFailedStatusTaskFactory, times(1)).createRetryWithSetNotificationStatusFailedTaskOnExhaust(any(), taskName.capture(), anyString());
        assertThat(taskName.getValue(), is(UPLOAD_MATERIAL_TO_STORAGE_TASK));
    }

    private void assertOnMaterialTasData(final MaterialTaskData infoData, final String caseId, final String materialId, final String defendantId) {
        assertThat(infoData.getCaseId(), is(caseId));
        assertThat(infoData.getMaterialId(), is(materialId));
        if(StringUtils.isNotEmpty(defendantId)){
            assertThat(infoData.getDefendantIdReferralIdMap(), notNullValue());
            assertThat(infoData.getDefendantIdReferralIdMap().get(defendantId), notNullValue());
        }
    }

    private MaterialTaskData getMaterialTaskData(final String caseId, final List<String> defendantIdList , final String tranRefId, final String materialId, final String documentTypeAccessId) {
        MaterialTaskData taskData = new MaterialTaskData();
        taskData.setMaterialId(materialId);
        taskData.setTranRefId(tranRefId);
        taskData.setCaseId(caseId);
        if (CollectionUtils.isNotEmpty(defendantIdList)) {
            taskData.setDefendantIdReferralIdMap(defendantIdList.stream().collect(Collectors.toMap(defId -> defId.toString(), defValue -> randomUUID().toString())));
        }
        taskData.setDocumentDate(LocalDate.now().toString());
        taskData.setDocumentName(random(15));
        taskData.setCaseUrn(random(9));
        taskData.setCaseReferralId(randomUUID().toString());
        taskData.setDocumentTypeAccessId(documentTypeAccessId);
        return taskData;
    }

    private ExecutionInfo returnCompletedExecutionInfo(){
        return executionInfo()
                .withExecutionStatus(COMPLETED)
                .build();
    }

}
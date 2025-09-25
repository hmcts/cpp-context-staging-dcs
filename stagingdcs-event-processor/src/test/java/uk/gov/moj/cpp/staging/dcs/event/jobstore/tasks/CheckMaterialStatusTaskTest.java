package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.hamcrest.MatcherAssert.assertThat;
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
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.CHECK_MATERIAL_STATUS_TASK;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.MaterialService;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.JsonObject;

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
class CheckMaterialStatusTaskTest {

    @InjectMocks
    private CheckMaterialStatusTask checkMaterialStatusTask;

    @Mock
    DcsOperationHelper dcsOperationHelper;
    @Spy
    private JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(new ObjectMapperProducer().objectMapper());
    @Spy
    private ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(new ObjectMapperProducer().objectMapper());

    @Mock
    private Logger logger;

    @Mock
    MaterialService materialService;

    @Mock
    private SetFailedStatusTaskFactory setFailedStatusTaskFactory;


    @Test
    void executeMethodShouldHandlePostSuccess() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId = randomUUID().toString();
        final String materialId2 = randomUUID().toString();
        final String tranRefId = randomUUID().toString();
        final String documentTypeId = randomUUID().toString();
        final MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), tranRefId, materialId, documentTypeId);

        when(dcsOperationHelper.returnCompletedExecutionInfo()).thenReturn(returnCompletedExecutionInfo());
        when(materialService.getDownloadableMaterialMap(any())).thenReturn(getDownloadableMaterialMap(materialId, true, materialId2, false));

        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                CHECK_MATERIAL_STATUS_TASK,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        checkMaterialStatusTask.execute(materialExecutionInfo);

        final ArgumentCaptor<JsonObject> uploadStorageTask = ArgumentCaptor.forClass(JsonObject.class);
        verify(dcsOperationHelper, times(1)).initiateUploadToStorageTask(uploadStorageTask.capture());
        final JsonObject storageJsonData = uploadStorageTask.getValue();
        final MaterialTaskData storageData = jsonObjectToObjectConverter.convert(storageJsonData, MaterialTaskData.class);
        assertOnMaterialTasData(storageData, caseId, materialId, defendantId);

        verify(dcsOperationHelper, times(1)).returnCompletedExecutionInfo();
    }

    @Test
    void executeMethodShouldDoRetry_WhenMaterialDataIsNotPresent() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId = randomUUID().toString();
        final String tranRefId = randomUUID().toString();
        final String documentTypeId = randomUUID().toString();
        final MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), tranRefId, materialId, documentTypeId);

        when(materialService.getDownloadableMaterialMap(any())).thenReturn(getDownloadableMaterialMap(randomUUID().toString(), true, randomUUID().toString(), false));

        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                CHECK_MATERIAL_STATUS_TASK,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        checkMaterialStatusTask.execute(materialExecutionInfo);

        verify(dcsOperationHelper, times(0)).initiateUploadToStorageTask(any());
        verify(dcsOperationHelper, times(0)).returnCompletedExecutionInfo();
        final ArgumentCaptor<String> taskName = ArgumentCaptor.forClass(String.class);
        verify(setFailedStatusTaskFactory, times(1)).createRetryWithSetNotificationStatusFailedTaskOnExhaust(any(), taskName.capture(), anyString());
        assertThat(taskName.getValue(), is(CHECK_MATERIAL_STATUS_TASK));
    }

    @Test
    void executeMethodShouldDoRetry_WhenMaterialIsNotDownloadable() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId = randomUUID().toString();
        final String tranRefId = randomUUID().toString();
        final String documentTypeId = randomUUID().toString();
        final MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), tranRefId, materialId, documentTypeId);

        when(materialService.getDownloadableMaterialMap(any())).thenReturn(getDownloadableMaterialMap(materialId, false, randomUUID().toString(), false));

        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                CHECK_MATERIAL_STATUS_TASK,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        checkMaterialStatusTask.execute(materialExecutionInfo);

        verify(dcsOperationHelper, times(0)).initiateUploadToStorageTask(any());
        verify(dcsOperationHelper, times(0)).returnCompletedExecutionInfo();
        final ArgumentCaptor<String> taskName = ArgumentCaptor.forClass(String.class);
        verify(setFailedStatusTaskFactory, times(1)).createRetryWithSetNotificationStatusFailedTaskOnExhaust(any(), taskName.capture(), anyString());
        assertThat(taskName.getValue(), is(CHECK_MATERIAL_STATUS_TASK));
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
            taskData.setDefendantIdReferralIdMap(defendantIdList.stream().collect(toMap(defId -> defId, defValue -> randomUUID().toString())));
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

    public Map<String, Boolean> getDownloadableMaterialMap(final String materialId1, final boolean firstBoolean, final String materialId2, final boolean secondBoolean) {
        final Map<String, Boolean> map = new HashMap<>();
        map.put(materialId1, firstBoolean);
        map.put(materialId2, secondBoolean);
        return map;
    }


}
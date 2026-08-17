package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.dcs.openapi.model.ErrorResponsePayload.ErrorCodeEnum.CASE_HAS_SPLIT_OR_MERGED;
import static uk.gov.hmcts.dcs.openapi.model.ErrorResponsePayload.ErrorCodeEnum.CASE_NOT_FOUND;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.RETRY;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.SEND_MATERIAL_TO_DCS_TASK;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.staging.dcs.domain.common.Constants;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsNotificationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsRestNotificationService;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.Response;

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
class SendMaterialToDcsTaskTest {

    @InjectMocks
    private SendMaterialToDcsTask sendMaterialToDcsTask;

    @Mock
    DcsOperationHelper dcsOperationHelper;

    @Mock
    DcsNotificationHelper dcsNotificationHelper;

    @Mock
    DcsRestNotificationService dcsRestNotificationService;
    @Spy
    private JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(new ObjectMapperProducer().objectMapper());
    @Spy
    private ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(new ObjectMapperProducer().objectMapper());
    @Mock
    private DcsCaseDetailRepository dcsCaseDetailRepository;
    @Mock
    private TransactionDetailRepository transactionDetailRepository;
    @Mock
    private TransactionMetadataRepository transactionMetadataRepository;

    @Mock
    private Logger logger;
    @Mock
    private SetFailedStatusTaskFactory setFailedStatusTaskFactory;

    @Test
    void executeMethodShouldHandlePostSuccess() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId = randomUUID().toString();
        final String tranRefId = randomUUID().toString();
        final String documentTypeId = randomUUID().toString();

        Response response = Response.accepted().build();

        when(dcsOperationHelper.returnCompletedExecutionInfo()).thenReturn(returnCompletedExecutionInfo());
        when(dcsRestNotificationService.submitMaterial(any(),any())).thenReturn(response);

        final MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), tranRefId, materialId, documentTypeId);
        inputData.setDocumentSection("documentSection");
        inputData.setAzureStorageUrl("https://azureUrl/blobstorage?materialId=".concat(materialId));
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                SEND_MATERIAL_TO_DCS_TASK,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        sendMaterialToDcsTask.execute(materialExecutionInfo);

        verify(dcsOperationHelper, times(1)).returnCompletedExecutionInfo();

        final ArgumentCaptor<MaterialTaskData> tranMetaData = ArgumentCaptor.forClass(MaterialTaskData.class);
        final ArgumentCaptor<String> statusCapture = ArgumentCaptor.forClass(String.class);
        verify(dcsNotificationHelper, times(1)).saveOrUpdateMaterialMetadata(tranMetaData.capture(),statusCapture.capture() );
        final MaterialTaskData metadata = tranMetaData.getValue();
        assertOnMaterialTaskData(metadata, caseId, materialId, defendantId);
        assertThat(statusCapture.getValue(), is(TransactionStatus.SENT.name()));

        final ArgumentCaptor<UUID> tranId = ArgumentCaptor.forClass(UUID.class);
        verify(dcsNotificationHelper, times(1)).saveOrUpdateTransactionDetails(tranId.capture(),any(), any(),any(),any(),any());
        assertThat(tranId.getValue(), is(fromString(tranRefId)));
    }
    @Test
    void executeMethodShouldHandle_BadRequest() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId = randomUUID().toString();
        final String tranRefId = randomUUID().toString();
        final String documentTypeId = randomUUID().toString();

        Response response = Response.status(400)
                        .entity(createObjectBuilder()
                                .add(Constants.ERROR_MESSAGE, "there is error")
                                .build()
                                .toString())
                .build();

        when(dcsOperationHelper.returnCompletedExecutionInfo()).thenReturn(returnCompletedExecutionInfo());
        when(dcsRestNotificationService.submitMaterial(any(),any())).thenReturn(response);

        final MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), tranRefId, materialId, documentTypeId);
        inputData.setDocumentSection("documentSection");
        inputData.setAzureStorageUrl("https://azureUrl/blobstorage?materialId=".concat(materialId));
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                SEND_MATERIAL_TO_DCS_TASK,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        sendMaterialToDcsTask.execute(materialExecutionInfo);

        verify(dcsOperationHelper, times(1)).returnCompletedExecutionInfo();

        final ArgumentCaptor<MaterialTaskData> tranMetaData = ArgumentCaptor.forClass(MaterialTaskData.class);
        final ArgumentCaptor<String> statusCapture = ArgumentCaptor.forClass(String.class);
        verify(dcsNotificationHelper, times(1)).saveOrUpdateMaterialMetadata(tranMetaData.capture(),statusCapture.capture() );
        final MaterialTaskData metadata = tranMetaData.getValue();
        assertOnMaterialTaskData(metadata, caseId, materialId, defendantId);
        assertThat(statusCapture.getValue(), is(TransactionStatus.FAILED.name()));

        final ArgumentCaptor<UUID> tranId = ArgumentCaptor.forClass(UUID.class);
        final ArgumentCaptor<String> tranDetailStatusCapture = ArgumentCaptor.forClass(String.class);
        verify(dcsNotificationHelper, times(1)).saveOrUpdateTransactionDetails(tranId.capture(),any(), any(),tranDetailStatusCapture.capture(),any(),any());
        assertThat(tranId.getValue(), is(fromString(tranRefId)));
        assertThat(tranDetailStatusCapture.getValue(), is(TransactionStatus.FAILED.name()));
    }

    @Test
    void executeMethodShouldHandle_404Errors() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId = randomUUID().toString();
        final String tranRefId = randomUUID().toString();
        final String documentTypeId = randomUUID().toString();

        Response response = Response.status(404)
                .entity(createObjectBuilder()
                        .add(Constants.ERROR_MESSAGE, CASE_NOT_FOUND.getValue())
                        .add(Constants.ERROR_CODE, CASE_NOT_FOUND.getValue())
                        .build()
                        .toString())
                .build();

        when(dcsOperationHelper.returnCompletedExecutionInfo()).thenReturn(returnCompletedExecutionInfo());
        when(dcsRestNotificationService.submitMaterial(any(),any())).thenReturn(response);

        final MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), tranRefId, materialId, documentTypeId);
        inputData.setDocumentSection("documentSection");
        inputData.setAzureStorageUrl("https://azureUrl/blobstorage?materialId=".concat(materialId));
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                SEND_MATERIAL_TO_DCS_TASK,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        sendMaterialToDcsTask.execute(materialExecutionInfo);

        verify(dcsOperationHelper, times(1)).returnCompletedExecutionInfo();

        final ArgumentCaptor<MaterialTaskData> tranMetaData = ArgumentCaptor.forClass(MaterialTaskData.class);
        final ArgumentCaptor<String> statusCapture = ArgumentCaptor.forClass(String.class);
        verify(dcsNotificationHelper, times(1)).saveOrUpdateMaterialMetadata(tranMetaData.capture(),statusCapture.capture() );
        final MaterialTaskData metadata = tranMetaData.getValue();
        assertOnMaterialTaskData(metadata, caseId, materialId, defendantId);
        assertThat(statusCapture.getValue(), is(TransactionStatus.FAILED.name()));

        final ArgumentCaptor<UUID> tranId = ArgumentCaptor.forClass(UUID.class);
        final ArgumentCaptor<String> tranDetailStatusCapture = ArgumentCaptor.forClass(String.class);
        verify(dcsNotificationHelper, times(1)).saveOrUpdateTransactionDetails(tranId.capture(),any(),any(),tranDetailStatusCapture.capture(),any(),any());
        assertThat(tranId.getValue(), is(fromString(tranRefId)));
        assertThat(tranDetailStatusCapture.getValue(), is(TransactionStatus.FAILED.name()));
    }

    @Test
    void executeMethodShouldHandle_404SplitAndMerge() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId = randomUUID().toString();
        final String tranRefId = randomUUID().toString();
        final String documentTypeId = randomUUID().toString();

        Response response = Response.status(404)
                .entity(createObjectBuilder()
                        .add(Constants.ERROR_MESSAGE, "case has been merged")
                        .add(Constants.ERROR_CODE, CASE_HAS_SPLIT_OR_MERGED.getValue())
                        .build()
                        .toString())
                .build();

        when(dcsOperationHelper.returnCompletedExecutionInfo()).thenReturn(returnCompletedExecutionInfo());
        when(dcsRestNotificationService.submitMaterial(any(),any())).thenReturn(response);

        final MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), tranRefId, materialId, documentTypeId);
        inputData.setDocumentSection("documentSection");
        inputData.setAzureStorageUrl("https://azureUrl/blobstorage?materialId=".concat(materialId));
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                SEND_MATERIAL_TO_DCS_TASK,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        sendMaterialToDcsTask.execute(materialExecutionInfo);

        verify(dcsOperationHelper, times(1)).returnCompletedExecutionInfo();

        final ArgumentCaptor<MaterialTaskData> tranMetaData = ArgumentCaptor.forClass(MaterialTaskData.class);
        final ArgumentCaptor<String> statusCapture = ArgumentCaptor.forClass(String.class);
        verify(dcsNotificationHelper, times(1)).saveOrUpdateMaterialMetadata(tranMetaData.capture(),statusCapture.capture() );
        final MaterialTaskData metadata = tranMetaData.getValue();
        assertOnMaterialTaskData(metadata, caseId, materialId, defendantId);
        assertThat(statusCapture.getValue(), is(TransactionStatus.FAILED.name()));

        final ArgumentCaptor<UUID> tranId = ArgumentCaptor.forClass(UUID.class);
        final ArgumentCaptor<String> tranDetailStatusCapture = ArgumentCaptor.forClass(String.class);
        verify(dcsNotificationHelper, times(1)).saveOrUpdateTransactionDetails(tranId.capture(),any(),any(),tranDetailStatusCapture.capture(),any(),any());
        assertThat(tranId.getValue(), is(fromString(tranRefId)));
        assertThat(tranDetailStatusCapture.getValue(), is(TransactionStatus.FAILED.name()));

        verify(dcsOperationHelper, times(1)).unlinkByCaseIdIfErrorsPresent(CASE_HAS_SPLIT_OR_MERGED.getValue()+": case has been merged", fromString(caseId));
    }

    @Test
    void executeMethodShouldHandle_GeneralException() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId = randomUUID().toString();
        final String tranRefId = randomUUID().toString();
        final String documentTypeId = randomUUID().toString();

        Response response = Response.status(500)
                .entity(createObjectBuilder()
                        .add(Constants.ERROR_MESSAGE, CASE_NOT_FOUND.getValue())
                        .add(Constants.ERROR_CODE, CASE_NOT_FOUND.getValue())
                        .build()
                        .toString())
                .build();

        when(dcsRestNotificationService.submitMaterial(any(),any())).thenReturn(response);
        when(transactionMetadataRepository.findByTransactionReferenceId(any())).thenReturn(null);
        when(transactionDetailRepository.findByTransactionReferenceId(any())).thenReturn(null);

        final MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), tranRefId, materialId, documentTypeId);
        inputData.setDocumentSection("documentSection");
        inputData.setAzureStorageUrl("https://azureUrl/blobstorage?materialId=".concat(materialId));
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                SEND_MATERIAL_TO_DCS_TASK,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        sendMaterialToDcsTask.execute(materialExecutionInfo);

        verify(dcsOperationHelper, times(0)).returnCompletedExecutionInfo();

        final ArgumentCaptor<MaterialTaskData> tranMetaData = ArgumentCaptor.forClass(MaterialTaskData.class);
        final ArgumentCaptor<String> statusCapture = ArgumentCaptor.forClass(String.class);
        verify(dcsNotificationHelper, times(1)).saveOrUpdateMaterialMetadata(tranMetaData.capture(),statusCapture.capture() );
        final MaterialTaskData metadata = tranMetaData.getValue();
        assertOnMaterialTaskData(metadata, caseId, materialId, defendantId);
        assertThat(statusCapture.getValue(), is(RETRY.name()));

        final ArgumentCaptor<UUID> tranId = ArgumentCaptor.forClass(UUID.class);
        final ArgumentCaptor<String> tranDetailStatusCapture = ArgumentCaptor.forClass(String.class);
        verify(dcsNotificationHelper, times(1)).saveOrUpdateTransactionDetails(tranId.capture(),any(),any(),tranDetailStatusCapture.capture(),any(),any());
        assertThat(tranId.getValue(), is(fromString(tranRefId)));
        assertThat(tranDetailStatusCapture.getValue(), is(RETRY.name()));

        final ArgumentCaptor<String> taskName = ArgumentCaptor.forClass(String.class);
        verify(setFailedStatusTaskFactory, times(1)).createRetryWithSetNotificationStatusFailedTaskOnExhaust(any(), taskName.capture(), anyString());
        assertThat(taskName.getValue(), is(SEND_MATERIAL_TO_DCS_TASK));
    }

    @Test
    void executeMethodShouldHandle_ProcessingException() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId = randomUUID().toString();
        final String tranRefId = randomUUID().toString();
        final String documentTypeId = randomUUID().toString();

        Response response = Response.status(500)
                .entity("non json string for processing exception")
                .build();

        when(dcsRestNotificationService.submitMaterial(any(),any())).thenReturn(response);
        when(transactionMetadataRepository.findByTransactionReferenceId(any())).thenReturn(null);
        when(transactionDetailRepository.findByTransactionReferenceId(any())).thenReturn(null);

        final MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), tranRefId, materialId, documentTypeId);
        inputData.setDocumentSection("documentSection");
        inputData.setAzureStorageUrl("https://azureUrl/blobstorage?materialId=".concat(materialId));
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                SEND_MATERIAL_TO_DCS_TASK,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        sendMaterialToDcsTask.execute(materialExecutionInfo);

        verify(dcsOperationHelper, times(0)).returnCompletedExecutionInfo();

        final ArgumentCaptor<MaterialTaskData> tranMetaData = ArgumentCaptor.forClass(MaterialTaskData.class);
        final ArgumentCaptor<String> statusCapture = ArgumentCaptor.forClass(String.class);
        verify(dcsNotificationHelper, times(1)).saveOrUpdateMaterialMetadata(tranMetaData.capture(),statusCapture.capture() );
        final MaterialTaskData metadata = tranMetaData.getValue();
        assertOnMaterialTaskData(metadata, caseId, materialId, defendantId);
        assertThat(statusCapture.getValue(), is(RETRY.name()));

        final ArgumentCaptor<UUID> tranId = ArgumentCaptor.forClass(UUID.class);
        final ArgumentCaptor<String> tranDetailStatusCapture = ArgumentCaptor.forClass(String.class);
        verify(dcsNotificationHelper, times(1)).saveOrUpdateTransactionDetails(tranId.capture(),any(),any(), tranDetailStatusCapture.capture(),any(),any());
        assertThat(tranId.getValue(), is(fromString(tranRefId)));
        assertThat(tranDetailStatusCapture.getValue(), is(RETRY.name()));

        final ArgumentCaptor<String> taskName = ArgumentCaptor.forClass(String.class);
        verify(setFailedStatusTaskFactory, times(1)).createRetryWithSetNotificationStatusFailedTaskOnExhaust(any(), taskName.capture(), anyString());
        assertThat(taskName.getValue(), is(SEND_MATERIAL_TO_DCS_TASK));
    }

    private void assertOnMaterialTaskData(final MaterialTaskData infoData, final String caseId, final String materialId, final String defendantId) {
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
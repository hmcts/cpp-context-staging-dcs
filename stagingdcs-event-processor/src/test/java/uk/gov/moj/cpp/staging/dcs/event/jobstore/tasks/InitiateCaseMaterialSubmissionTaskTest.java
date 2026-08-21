package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.INITIATE_MATERIAL_TASK_FOR_CASE;

import uk.gov.justice.courts.progression.query.CourtdocumentsAll;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.ProgressionService;
import uk.gov.moj.cpp.staging.dcs.event.service.ReferenceDataService;
import uk.gov.moj.cpp.staging.dcs.event.util.FileUtil;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsDefendantEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsDefendantRepository;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;

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
class InitiateCaseMaterialSubmissionTaskTest {

    @InjectMocks
    private InitiateCaseMaterialSubmissionTask initiateCaseMaterialSubmissionTask;

    @Mock
    DcsOperationHelper dcsOperationHelper;
    @Spy
    private JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(new ObjectMapperProducer().objectMapper());
    @Spy
    private ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(new ObjectMapperProducer().objectMapper());
    @Mock
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Mock
    private Logger logger;

    @Mock
    ProgressionService progressionService;

    @Mock
    ReferenceDataService referenceDataService;

    @Mock
    private ExecutionInfo executionInfo;

    @Mock
    private JsonObject jobData;
    @Mock
    private Response response;
    @Mock
    private SetFailedStatusTaskFactory setFailedStatusTaskFactory;
    @Mock
    private DcsDefendantRepository dcsDefendantRepository;

    @Test
    void executeMethodShouldHandlePostSuccess() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId1 = randomUUID().toString();
        final String materialId2 = randomUUID().toString();
        final String documentTypeId1 = randomUUID().toString();
        final String documentTypeId2 = randomUUID().toString();

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(fromString(caseId));
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(fromString(defendantId));
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());

        when(dcsCaseDetailRepository.findByCaseId(UUID.fromString(caseId))).thenReturn(List.of(dcsCaseDetailEntity));

        when(progressionService.getCourtDocumentsByParams(any(), any())).thenReturn(buildCourtDocumentIndexList(caseId, defendantId, materialId1, materialId2, documentTypeId1, documentTypeId2));
        when(referenceDataService.createDocumentAccessTypeMap()).thenReturn(createDocumentAccessTypeMap(documentTypeId1, true, documentTypeId2, true));
        when(dcsOperationHelper.shouldSendMaterialToDcs(any())).thenReturn(true);

        DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(UUID.fromString(defendantId));
        dcsDefendantEntity.setMasterDefendantId(randomUUID());
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);

        MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), null, null, null);
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                INITIATE_MATERIAL_TASK_FOR_CASE,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        initiateCaseMaterialSubmissionTask.execute(materialExecutionInfo);

        final ArgumentCaptor<JsonObject> eligibleTask = ArgumentCaptor.forClass(JsonObject.class);
        verify(dcsOperationHelper, times(1)).processInsertMaterialDocument(eligibleTask.capture());
        final List<JsonObject> downloadableJsonData = eligibleTask.getAllValues();
        assertThat(downloadableJsonData, hasSize(1));
        final MaterialTaskData downloadableTaskData1 = jsonObjectToObjectConverter.convert(downloadableJsonData.get(0), MaterialTaskData.class);
        assertOnMaterialTaskData(downloadableTaskData1, caseId, materialId1, null);
        final ArgumentCaptor<String> taskName = ArgumentCaptor.forClass(String.class);
        verify(setFailedStatusTaskFactory, times(1)).createRetryWithSetNotificationStatusFailedTaskOnExhaust(any(), taskName.capture(), anyString());
        assertThat(taskName.getValue(), is(INITIATE_MATERIAL_TASK_FOR_CASE));

    }

    @Test
    void executeMethodShouldProcess_WhenDefendantIdMatchesLinkedDefendantId() {
        final String linkedDefendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId1 = randomUUID().toString();
        final String materialId2 = randomUUID().toString();
        final String documentTypeId1 = randomUUID().toString();
        final String documentTypeId2 = randomUUID().toString();

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(fromString(caseId));
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(fromString(linkedDefendantId));
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByCaseId(UUID.fromString(caseId))).thenReturn(List.of(dcsCaseDetailEntity));

        when(progressionService.getCourtDocumentsByParams(any(), any()))
                .thenReturn(buildCourtDocumentIndexListWithAllowedDefendantDocument(caseId, linkedDefendantId, materialId1, materialId2, documentTypeId1, documentTypeId2));
        when(referenceDataService.createDocumentAccessTypeMap()).thenReturn(createDocumentAccessTypeMap(documentTypeId1, false, documentTypeId2, true));
        when(dcsOperationHelper.shouldSendMaterialToDcs(any())).thenReturn(true);

        DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(UUID.fromString(linkedDefendantId));
        dcsDefendantEntity.setMasterDefendantId(randomUUID());
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);

        MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(linkedDefendantId), null, null, null);
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                INITIATE_MATERIAL_TASK_FOR_CASE,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        initiateCaseMaterialSubmissionTask.execute(materialExecutionInfo);

        final ArgumentCaptor<JsonObject> eligibleTask = ArgumentCaptor.forClass(JsonObject.class);
        verify(dcsOperationHelper, times(1)).processInsertMaterialDocument(eligibleTask.capture());
        final MaterialTaskData downloadableTaskData = jsonObjectToObjectConverter.convert(eligibleTask.getValue(), MaterialTaskData.class);
        assertOnMaterialTaskData(downloadableTaskData, caseId, materialId2, linkedDefendantId);
    }

    @Test
    void executeMethodShouldProcess_WhenDefendantIdMatchesMasterDefendantId() {
        final String linkedDefendantId = randomUUID().toString();
        final String masterDefendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId1 = randomUUID().toString();
        final String materialId2 = randomUUID().toString();
        final String documentTypeId1 = randomUUID().toString();
        final String documentTypeId2 = randomUUID().toString();

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(fromString(caseId));
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(fromString(linkedDefendantId));
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByCaseId(UUID.fromString(caseId))).thenReturn(List.of(dcsCaseDetailEntity));

        when(progressionService.getCourtDocumentsByParams(any(), any()))
                .thenReturn(buildCourtDocumentIndexListWithAllowedDefendantDocument(caseId, masterDefendantId, materialId1, materialId2, documentTypeId1, documentTypeId2));
        when(referenceDataService.createDocumentAccessTypeMap()).thenReturn(createDocumentAccessTypeMap(documentTypeId1, false, documentTypeId2, true));
        when(dcsOperationHelper.shouldSendMaterialToDcs(any())).thenReturn(true);

        DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(UUID.fromString(linkedDefendantId));
        dcsDefendantEntity.setMasterDefendantId(UUID.fromString(masterDefendantId));
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);

        MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(linkedDefendantId), null, null, null);
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                INITIATE_MATERIAL_TASK_FOR_CASE,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        initiateCaseMaterialSubmissionTask.execute(materialExecutionInfo);

        final ArgumentCaptor<JsonObject> eligibleTask = ArgumentCaptor.forClass(JsonObject.class);
        verify(dcsOperationHelper, times(1)).processInsertMaterialDocument(eligibleTask.capture());
        final MaterialTaskData downloadableTaskData = jsonObjectToObjectConverter.convert(eligibleTask.getValue(), MaterialTaskData.class);
        assertOnMaterialTaskData(downloadableTaskData, caseId, materialId2, linkedDefendantId);
    }
    @Test
    void executeMethodShouldNotProcess_DueToDuplicateMaterial() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId1 = randomUUID().toString();
        final String materialId2 = randomUUID().toString();
        final String documentTypeId1 = randomUUID().toString();
        final String documentTypeId2 = randomUUID().toString();

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(fromString(caseId));
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(fromString(defendantId));
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());

        when(dcsCaseDetailRepository.findByCaseId(UUID.fromString(caseId))).thenReturn(List.of(dcsCaseDetailEntity));
        when(dcsOperationHelper.shouldSendMaterialToDcs(any())).thenReturn(false);

        when(progressionService.getCourtDocumentsByParams(any(), any())).thenReturn(buildCourtDocumentIndexList(caseId, defendantId, materialId1, materialId2, documentTypeId1, documentTypeId2));
        when(referenceDataService.createDocumentAccessTypeMap()).thenReturn(createDocumentAccessTypeMap(documentTypeId1, true, documentTypeId2, true));

        DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(UUID.fromString(defendantId));
        dcsDefendantEntity.setMasterDefendantId(randomUUID());
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);

        MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), null, null, null);
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                INITIATE_MATERIAL_TASK_FOR_CASE,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        initiateCaseMaterialSubmissionTask.execute(materialExecutionInfo);

        verify(dcsOperationHelper, times(0)).processInsertMaterialDocument(any());
        verify(referenceDataService, times(1)).createDocumentAccessTypeMap();
        verify(progressionService, times(1)).getCourtDocumentsByParams(any(),any());
    }

    @Test
    void executeMethodShouldNotProcess_DueToMaterialNoEligibleForDistribution() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String materialId1 = randomUUID().toString();
        final String materialId2 = randomUUID().toString();
        final String documentTypeId1 = randomUUID().toString();
        final String documentTypeId2 = randomUUID().toString();

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(fromString(caseId));
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(fromString(defendantId));
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());

        when(dcsCaseDetailRepository.findByCaseId(UUID.fromString(caseId))).thenReturn(List.of(dcsCaseDetailEntity));
        when(dcsOperationHelper.shouldSendMaterialToDcs(any())).thenReturn(false);

        when(progressionService.getCourtDocumentsByParams(any(), any())).thenReturn(buildCourtDocumentIndexList(caseId, defendantId, materialId1, materialId2, documentTypeId1, documentTypeId2));
        when(referenceDataService.createDocumentAccessTypeMap()).thenReturn(createDocumentAccessTypeMap(documentTypeId1, true, documentTypeId2, true));

        DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(UUID.fromString(defendantId));
        dcsDefendantEntity.setMasterDefendantId(randomUUID());
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);

        MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), null, null, null);
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                INITIATE_MATERIAL_TASK_FOR_CASE,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        initiateCaseMaterialSubmissionTask.execute(materialExecutionInfo);

        verify(dcsOperationHelper, times(0)).processInsertMaterialDocument(any());
        verify(referenceDataService, times(1)).createDocumentAccessTypeMap();
        verify(progressionService, times(1)).getCourtDocumentsByParams(any(),any());
    }

    @Test
    void executeMethodShouldNotProcess_DueToUnlinkedDefendants() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();

        when(dcsCaseDetailRepository.findByCaseId(UUID.fromString(caseId))).thenReturn(Collections.emptyList());

        MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), null, null, null);
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                INITIATE_MATERIAL_TASK_FOR_CASE,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        initiateCaseMaterialSubmissionTask.execute(materialExecutionInfo);

        verify(dcsOperationHelper, times(0)).returnCompletedExecutionInfo();
        verify(referenceDataService, times(0)).createDocumentAccessTypeMap();
        verify(dcsOperationHelper, times(0)).initiateUploadToStorageTask(any());
        verify(dcsOperationHelper, times(0)).processInsertMaterialDocument(any());
        final ArgumentCaptor<String> taskName = ArgumentCaptor.forClass(String.class);
        verify(setFailedStatusTaskFactory, times(1)).createRetryWithSetNotificationStatusFailedTaskOnExhaust(any(), taskName.capture(), anyString());
        assertThat(taskName.getValue(), is(INITIATE_MATERIAL_TASK_FOR_CASE));
    }

    @Test
    void executeMethodShouldNotProcess_DueToUnMatchedDefendants() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(fromString(caseId));
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(randomUUID());
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());

        when(dcsCaseDetailRepository.findByCaseId(UUID.fromString(caseId))).thenReturn(List.of(dcsCaseDetailEntity));

        MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), null, null, null);
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                INITIATE_MATERIAL_TASK_FOR_CASE,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        initiateCaseMaterialSubmissionTask.execute(materialExecutionInfo);

        verify(dcsOperationHelper, times(0)).returnCompletedExecutionInfo();
        verify(referenceDataService, times(0)).createDocumentAccessTypeMap();
        verify(dcsOperationHelper, times(0)).processInsertMaterialDocument(any());
        final ArgumentCaptor<String> taskName = ArgumentCaptor.forClass(String.class);
        verify(setFailedStatusTaskFactory, times(1)).createRetryWithSetNotificationStatusFailedTaskOnExhaust(any(), taskName.capture(), anyString());
        assertThat(taskName.getValue(), is(INITIATE_MATERIAL_TASK_FOR_CASE));
    }

    @Test
    void executeMethodShouldNotProcess_DueToNoMaterialPresentForTheCase() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(fromString(caseId));
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(fromString(defendantId));
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());

        when(dcsCaseDetailRepository.findByCaseId(UUID.fromString(caseId))).thenReturn(List.of(dcsCaseDetailEntity));
        when(progressionService.getCourtDocumentsByParams(any(),any())).thenReturn(new CourtdocumentsAll(null, null));

        MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), null, null, null);
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                INITIATE_MATERIAL_TASK_FOR_CASE,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        initiateCaseMaterialSubmissionTask.execute(materialExecutionInfo);

        verify(referenceDataService, times(0)).createDocumentAccessTypeMap();
        verify(dcsOperationHelper, times(0)).processInsertMaterialDocument(any());
        final ArgumentCaptor<String> taskName = ArgumentCaptor.forClass(String.class);
        verify(setFailedStatusTaskFactory, times(1)).createRetryWithSetNotificationStatusFailedTaskOnExhaust(any(), taskName.capture(), anyString());
        assertThat(taskName.getValue(), is(INITIATE_MATERIAL_TASK_FOR_CASE));
    }


    @Test
    void executeMethodShouldDoRetry() {
        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();

        when(dcsCaseDetailRepository.findByCaseId(UUID.fromString(caseId))).thenReturn(List.of(dcsCaseDetailEntity));

        MaterialTaskData inputData = getMaterialTaskData(caseId, List.of(defendantId), null, null, null);
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(inputData),
                INITIATE_MATERIAL_TASK_FOR_CASE,
                ZonedDateTime.now(),
                STARTED,
                Priority.MEDIUM);
        initiateCaseMaterialSubmissionTask.execute(materialExecutionInfo);

        verify(dcsOperationHelper, times(0)).returnCompletedExecutionInfo();
        verify(referenceDataService, times(0)).createDocumentAccessTypeMap();
        verify(dcsOperationHelper, times(0)).processInsertMaterialDocument(any());
        final ArgumentCaptor<String> taskName = ArgumentCaptor.forClass(String.class);
        verify(setFailedStatusTaskFactory, times(1)).createRetryWithSetNotificationStatusFailedTaskOnExhaust(any(), taskName.capture(), anyString());
        assertThat(taskName.getValue(), is(INITIATE_MATERIAL_TASK_FOR_CASE));
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
        if (isNotEmpty(defendantIdList)) {
            taskData.setDefendantIdReferralIdMap(defendantIdList.stream().collect(toMap(defId -> defId, defValue -> randomUUID().toString())));
        }
        taskData.setDocumentDate(LocalDate.now().toString());
        taskData.setDocumentName(random(15));
        taskData.setCaseUrn(random(9));
        taskData.setCaseReferralId(randomUUID().toString());
        taskData.setDocumentTypeAccessId(documentTypeAccessId);
        return taskData;
    }

    private CourtdocumentsAll buildCourtDocumentIndexList(final String caseId,final String defendantId, final String materialId1, final String materialId2,
                                                                 final String documentId1, final String documentId2) {
        final String progressionString = FileUtil.getPayload("all-progression-courtdocument-search.json")
                .replaceAll("CASE_ID", caseId)
                .replaceAll("DEFENDANT_ID", defendantId)
                .replaceAll("MATERIAL_ID_1", materialId1)
                .replaceAll("MATERIAL_ID_2", materialId2)
                .replaceAll("DOCUMENT_TYPE_ID_1", documentId1)
                .replaceAll("DOCUMENT_TYPE_ID_2", documentId2);
        final JsonObject payloadJsonObject = FileUtil.jsonFromString(progressionString);
        return new JsonObjectToObjectConverter(new ObjectMapperProducer().objectMapper()).convert(payloadJsonObject, CourtdocumentsAll.class);
    }

    private CourtdocumentsAll buildCourtDocumentIndexListWithAllowedDefendantDocument(final String caseId, final String defendantId, final String materialId1,
                                                                                      final String materialId2, final String documentId1, final String documentId2) {
        final String progressionString = FileUtil.getPayload("all-progression-courtdocument-search.json")
                .replaceAll("CASE_ID", caseId)
                .replaceAll("DEFENDANT_ID", defendantId)
                .replaceAll("MATERIAL_ID_1", materialId1)
                .replaceAll("MATERIAL_ID_2", materialId2)
                .replaceAll("DOCUMENT_TYPE_ID_1", documentId1)
                .replaceAll("DOCUMENT_TYPE_ID_2", documentId2)
                .replaceAll("application/zip", "application/pdf");
        final JsonObject payloadJsonObject = FileUtil.jsonFromString(progressionString);
        return new JsonObjectToObjectConverter(new ObjectMapperProducer().objectMapper()).convert(payloadJsonObject, CourtdocumentsAll.class);
    }
    public Map<String,Boolean> createDocumentAccessTypeMap(final String documentTypeId1, final boolean firstBoolean, final String documentTypeId2, final boolean secondBoolean) {
        final Map<String, Boolean> documentTypeMap = new HashMap<>();
        documentTypeMap.put(documentTypeId1, firstBoolean);
        documentTypeMap.put(documentTypeId2, secondBoolean);
        return documentTypeMap;
    }

}
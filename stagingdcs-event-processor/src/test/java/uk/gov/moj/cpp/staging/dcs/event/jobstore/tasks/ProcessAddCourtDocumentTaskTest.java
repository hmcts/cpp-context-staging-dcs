package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.util.Collections.EMPTY_LIST;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.PROCESS_ADD_COURT_DOCUMENT_TASK;
import static uk.gov.moj.cpp.staging.dcs.event.util.FileUtil.getPayload;
import static uk.gov.moj.cpp.staging.dcs.event.util.FileUtil.jsonFromString;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.ReferenceDataService;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsDefendantEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsDefendantRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.json.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class ProcessAddCourtDocumentTaskTest {

    @InjectMocks
    private ProcessAddCourtDocumentTask processAddCourtDocumentTask;

    @Captor
    private ArgumentCaptor<ExecutionInfo> executionInfoCaptor;

    @Captor
    private ArgumentCaptor<String> taskName;
    @Mock
    private Logger logger;
    @Spy
    private static ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(new ObjectMapperProducer().objectMapper());

    @Spy
    private  static JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(new ObjectMapperProducer().objectMapper());
    @Mock
    private UtcClock clock;
    @Mock
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Mock
    private DcsOperationHelper dcsOperationHelper;

    @Mock
    ReferenceDataService referenceDataService;

    @Mock
    SetFailedStatusTaskFactory setFailedStatusTaskFactory;

    @Mock
    DcsDefendantRepository dcsDefendantRepository;

    @BeforeAll
    public static void createObjectToJsonObjectConverter() {
        setField(objectToJsonObjectConverter, "mapper", new ObjectMapperProducer().objectMapper());
    }
    @Test
    void shouldProcessAddCourtDocumentTask_CheckMaterialStatusTask() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String courtDocumentString = getPayload("court-document-defendant-level.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());
        final JsonObject inputdataJsonObject = new StringToJsonObjectConverter().convert(courtDocumentString);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(caseId);
        dcsCaseDetailEntity.setCaseRefId(randomUUID());
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(defendantId);
        dcsCaseDetailEntity.setDefendantRefId(randomUUID());
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByDefendantId(defendantId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(dcsCaseDetailRepository.findByCaseId(caseId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(dcsOperationHelper.shouldSendMaterialToDcs(any())).thenReturn(true);
        when(referenceDataService.getDocumentTypeAccessById(any())).thenReturn(getReferenceDataDocumentType(documentTypeId.toString()));
        when(clock.now()).thenReturn(ZonedDateTime.now());

        final DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(defendantId);
        dcsDefendantEntity.setMasterDefendantId(defendantId);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);

        final ExecutionInfo addCourtDocumentExecutionInfo = new ExecutionInfo(
                inputdataJsonObject,
                PROCESS_ADD_COURT_DOCUMENT_TASK,
                clock.now().plusSeconds(10),
                STARTED,
                Priority.MEDIUM);

        processAddCourtDocumentTask.execute(addCourtDocumentExecutionInfo);

        final ArgumentCaptor<JsonObject> checkMaterialStatus = ArgumentCaptor.forClass(JsonObject.class);
        verify(dcsOperationHelper, times(1)).processInsertMaterialDocument(checkMaterialStatus.capture());
        final JsonObject checkMaterialTaskData = checkMaterialStatus.getValue();
        final MaterialTaskData data = jsonObjectToObjectConverter.convert(checkMaterialTaskData, MaterialTaskData.class);
        assertOnMaterialTasData(data, caseId.toString(), materialId.toString(), defendantId.toString());
    }

    @Test
    void shouldProcessAddCourtDocumentTask_WhenEventDefendantMatchesMapKey() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID masterDefendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String courtDocumentString = getPayload("court-document-defendant-level.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());
        final JsonObject inputdataJsonObject = new StringToJsonObjectConverter().convert(courtDocumentString);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(caseId);
        dcsCaseDetailEntity.setCaseRefId(randomUUID());
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(defendantId);
        dcsCaseDetailEntity.setDefendantRefId(randomUUID());
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByCaseId(caseId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(dcsCaseDetailRepository.findByDefendantId(defendantId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(dcsOperationHelper.shouldSendMaterialToDcs(any())).thenReturn(true);
        when(referenceDataService.getDocumentTypeAccessById(any())).thenReturn(getReferenceDataDocumentType(documentTypeId.toString()));
        when(clock.now()).thenReturn(ZonedDateTime.now());

        final DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(defendantId);
        dcsDefendantEntity.setMasterDefendantId(masterDefendantId);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);

        final ExecutionInfo addCourtDocumentExecutionInfo = new ExecutionInfo(
                inputdataJsonObject,
                PROCESS_ADD_COURT_DOCUMENT_TASK,
                clock.now().plusSeconds(10),
                STARTED,
                Priority.MEDIUM);

        processAddCourtDocumentTask.execute(addCourtDocumentExecutionInfo);

        final ArgumentCaptor<JsonObject> checkMaterialStatus = ArgumentCaptor.forClass(JsonObject.class);
        verify(dcsOperationHelper, times(1)).processInsertMaterialDocument(checkMaterialStatus.capture());
        final JsonObject checkMaterialTaskData = checkMaterialStatus.getValue();
        final MaterialTaskData data = jsonObjectToObjectConverter.convert(checkMaterialTaskData, MaterialTaskData.class);
        assertOnMaterialTasData(data, caseId.toString(), materialId.toString(), defendantId.toString());
    }

    @Test
    void shouldProcessAddCourtDocumentTask_WhenEventDefendantMatchesMapValue() {
        final UUID caseId = randomUUID();
        final UUID linkedDefendantId = randomUUID();
        final UUID masterDefendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String courtDocumentString = getPayload("court-document-defendant-level.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", masterDefendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());
        final JsonObject inputdataJsonObject = new StringToJsonObjectConverter().convert(courtDocumentString);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(caseId);
        dcsCaseDetailEntity.setCaseRefId(randomUUID());
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(linkedDefendantId);
        dcsCaseDetailEntity.setDefendantRefId(randomUUID());
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByCaseId(caseId)).thenReturn(List.of(dcsCaseDetailEntity));
        DcsCaseDetailEntity caseLookupEntity = new DcsCaseDetailEntity();
        caseLookupEntity.setId(randomUUID());
        caseLookupEntity.setCaseId(caseId);
        caseLookupEntity.setDefendantId(masterDefendantId);
        caseLookupEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByDefendantId(masterDefendantId)).thenReturn(List.of(caseLookupEntity));
        when(dcsOperationHelper.shouldSendMaterialToDcs(any())).thenReturn(true);
        when(referenceDataService.getDocumentTypeAccessById(any())).thenReturn(getReferenceDataDocumentType(documentTypeId.toString()));
        when(clock.now()).thenReturn(ZonedDateTime.now());

        final DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(linkedDefendantId);
        dcsDefendantEntity.setMasterDefendantId(masterDefendantId);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);

        final ExecutionInfo addCourtDocumentExecutionInfo = new ExecutionInfo(
                inputdataJsonObject,
                PROCESS_ADD_COURT_DOCUMENT_TASK,
                clock.now().plusSeconds(10),
                STARTED,
                Priority.MEDIUM);

        processAddCourtDocumentTask.execute(addCourtDocumentExecutionInfo);

        final ArgumentCaptor<JsonObject> checkMaterialStatus = ArgumentCaptor.forClass(JsonObject.class);
        verify(dcsOperationHelper, times(1)).processInsertMaterialDocument(checkMaterialStatus.capture());
        final JsonObject checkMaterialTaskData = checkMaterialStatus.getValue();
        final MaterialTaskData data = jsonObjectToObjectConverter.convert(checkMaterialTaskData, MaterialTaskData.class);
        assertOnMaterialTasData(data, caseId.toString(), materialId.toString(), linkedDefendantId.toString());
    }

    @Test
    void shouldProcessCaseLevelAddCourtDocumentTask_CheckMaterialStatusTask() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String courtDocumentString = getPayload("court-document-case-level.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());
        final JsonObject inputdataJsonObject = new StringToJsonObjectConverter().convert(courtDocumentString);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(caseId);
        dcsCaseDetailEntity.setCaseRefId(randomUUID());
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(defendantId);
        dcsCaseDetailEntity.setDefendantRefId(randomUUID());
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByCaseId(caseId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(dcsOperationHelper.shouldSendMaterialToDcs(any())).thenReturn(true);
        when(referenceDataService.getDocumentTypeAccessById(any())).thenReturn(getReferenceDataDocumentType(documentTypeId.toString()));
        when(clock.now()).thenReturn(ZonedDateTime.now());

        final ExecutionInfo addCourtDocumentExecutionInfo = new ExecutionInfo(
                inputdataJsonObject,
                PROCESS_ADD_COURT_DOCUMENT_TASK,
                clock.now().plusSeconds(10),
                STARTED,
                Priority.MEDIUM);

        processAddCourtDocumentTask.execute(addCourtDocumentExecutionInfo);

        final ArgumentCaptor<JsonObject> checkMaterialStatus = ArgumentCaptor.forClass(JsonObject.class);
        verify(dcsOperationHelper, times(1)).processInsertMaterialDocument(checkMaterialStatus.capture());
        final JsonObject checkMaterialTaskData = checkMaterialStatus.getValue();
        final MaterialTaskData data = jsonObjectToObjectConverter.convert(checkMaterialTaskData, MaterialTaskData.class);
        assertOnMaterialTasData(data, caseId.toString(), materialId.toString(), null);
    }

    @Test
    void shouldRetryProcessAddCourtDocumentTask_WhenCaseIdIsNotProcessable() {
        final UUID defendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String courtDocumentString = getPayload("court-document-defendant-level.json")
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());
        final JsonObject inputdataJsonObject = new StringToJsonObjectConverter().convert(courtDocumentString);
        when(clock.now()).thenReturn(ZonedDateTime.now());
        final ExecutionInfo addCourtDocumentExecutionInfo = new ExecutionInfo(
                inputdataJsonObject,
                PROCESS_ADD_COURT_DOCUMENT_TASK,
                clock.now().plusSeconds(10),
                STARTED,
                Priority.MEDIUM);

        processAddCourtDocumentTask.execute(addCourtDocumentExecutionInfo);
        verify(dcsOperationHelper, times(0)).processCheckMaterialStatus(any());
        final ArgumentCaptor<JsonObject> checkMaterialStatus = ArgumentCaptor.forClass(JsonObject.class);
        verify(dcsOperationHelper, times(0)).processCheckMaterialStatus(checkMaterialStatus.capture());
        verify(setFailedStatusTaskFactory, times(1)).createRetryWithSetNotificationStatusFailedTaskOnExhaust(any(), taskName.capture(), any());
        final String retryTaskName = taskName.getValue();
        assertThat(retryTaskName, is(PROCESS_ADD_COURT_DOCUMENT_TASK));
    }

    @Test
    void shouldCompleteAddCourtDocumentTask_WhenCaseIdIsNull() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String courtDocumentString = getPayload("court-document-defendant-level.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());
        final JsonObject inputdataJsonObject = new StringToJsonObjectConverter().convert(courtDocumentString);

        when(dcsCaseDetailRepository.findByDefendantId(defendantId)).thenReturn(EMPTY_LIST);
        when(clock.now()).thenReturn(ZonedDateTime.now());

        final ExecutionInfo addCourtDocumentExecutionInfo = new ExecutionInfo(
                inputdataJsonObject,
                PROCESS_ADD_COURT_DOCUMENT_TASK,
                clock.now().plusSeconds(10),
                STARTED,
                Priority.MEDIUM);

        processAddCourtDocumentTask.execute(addCourtDocumentExecutionInfo);
        verify(dcsOperationHelper, times(0)).processCheckMaterialStatus(any());
        final ArgumentCaptor<JsonObject> checkMaterialStatus = ArgumentCaptor.forClass(JsonObject.class);
        verify(dcsOperationHelper, times(0)).processCheckMaterialStatus(checkMaterialStatus.capture());
        verify(setFailedStatusTaskFactory, times(1)).createRetryWithSetNotificationStatusFailedTaskOnExhaust(any(), taskName.capture(), any());
        final String retryTaskName = taskName.getValue();
        assertThat(retryTaskName, is(PROCESS_ADD_COURT_DOCUMENT_TASK));
    }

    @Test
    void shouldCompleteAddCourtDocumentTask_WhenNoLinkedCases() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String courtDocumentString = getPayload("court-document-defendant-level.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());
        final JsonObject inputdataJsonObject = new StringToJsonObjectConverter().convert(courtDocumentString);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(caseId);
        dcsCaseDetailEntity.setCaseRefId(randomUUID());
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(defendantId);
        dcsCaseDetailEntity.setDefendantRefId(randomUUID());
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByDefendantId(defendantId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(dcsCaseDetailRepository.findByCaseId(caseId)).thenReturn(EMPTY_LIST);
        when(clock.now()).thenReturn(ZonedDateTime.now());

        final ExecutionInfo addCourtDocumentExecutionInfo = new ExecutionInfo(
                inputdataJsonObject,
                PROCESS_ADD_COURT_DOCUMENT_TASK,
                clock.now().plusSeconds(10),
                STARTED,
                Priority.MEDIUM);

        processAddCourtDocumentTask.execute(addCourtDocumentExecutionInfo);
        verify(dcsOperationHelper, times(0)).processCheckMaterialStatus(any());
        verify(dcsOperationHelper, times(1)).returnCompletedExecutionInfo();
    }

    @Test
    void shouldCompleteAddCourtDocumentTask_WhenMaterialIsAlreadySentToDcs() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String courtDocumentString = getPayload("court-document-defendant-level.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());
        final JsonObject inputdataJsonObject = new StringToJsonObjectConverter().convert(courtDocumentString);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(caseId);
        dcsCaseDetailEntity.setCaseRefId(randomUUID());
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(defendantId);
        dcsCaseDetailEntity.setDefendantRefId(randomUUID());
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByDefendantId(defendantId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(dcsCaseDetailRepository.findByCaseId(caseId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(clock.now()).thenReturn(ZonedDateTime.now());

        final DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(defendantId);
        dcsDefendantEntity.setMasterDefendantId(defendantId);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);

        final ExecutionInfo addCourtDocumentExecutionInfo = new ExecutionInfo(
                inputdataJsonObject,
                PROCESS_ADD_COURT_DOCUMENT_TASK,
                clock.now().plusSeconds(10),
                STARTED,
                Priority.MEDIUM);

        processAddCourtDocumentTask.execute(addCourtDocumentExecutionInfo);

        verify(dcsOperationHelper, times(1)).returnCompletedExecutionInfo();
        verify(dcsOperationHelper, times(0)).processCheckMaterialStatus(any());
    }

    @Test
    void shouldCompleteAddCourtDocumentTask_WhenMaterialIsNotEligibleForDistribution() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String courtDocumentString = getPayload("court-document-defendant-level.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());
        final JsonObject inputdataJsonObject = new StringToJsonObjectConverter().convert(courtDocumentString);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(caseId);
        dcsCaseDetailEntity.setCaseRefId(randomUUID());
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(defendantId);
        dcsCaseDetailEntity.setDefendantRefId(randomUUID());
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByDefendantId(defendantId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(dcsCaseDetailRepository.findByCaseId(caseId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(clock.now()).thenReturn(ZonedDateTime.now());

        final DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(defendantId);
        dcsDefendantEntity.setMasterDefendantId(defendantId);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);

        final ExecutionInfo addCourtDocumentExecutionInfo = new ExecutionInfo(
                inputdataJsonObject,
                PROCESS_ADD_COURT_DOCUMENT_TASK,
                clock.now().plusSeconds(10),
                STARTED,
                Priority.MEDIUM);

        processAddCourtDocumentTask.execute(addCourtDocumentExecutionInfo);

        verify(dcsOperationHelper, times(1)).returnCompletedExecutionInfo();
        verify(dcsOperationHelper, times(0)).processCheckMaterialStatus(any());
    }

    @Test
    void shouldCompleteAddCourtDocumentTask_WhenNoDocumentTypeAccessPresent() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String courtDocumentString = getPayload("court-document-defendant-level.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());
        final JsonObject inputdataJsonObject = new StringToJsonObjectConverter().convert(courtDocumentString);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(caseId);
        dcsCaseDetailEntity.setCaseRefId(randomUUID());
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(defendantId);
        dcsCaseDetailEntity.setDefendantRefId(randomUUID());
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByDefendantId(defendantId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(dcsCaseDetailRepository.findByCaseId(caseId)).thenReturn(List.of(dcsCaseDetailEntity));

        when(referenceDataService.getDocumentTypeAccessById(any())).thenReturn(Optional.empty());
        when(clock.now()).thenReturn(ZonedDateTime.now());

        final DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(defendantId);
        dcsDefendantEntity.setMasterDefendantId(defendantId);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);

        final ExecutionInfo addCourtDocumentExecutionInfo = new ExecutionInfo(
                inputdataJsonObject,
                PROCESS_ADD_COURT_DOCUMENT_TASK,
                clock.now().plusSeconds(10),
                STARTED,
                Priority.MEDIUM);

        processAddCourtDocumentTask.execute(addCourtDocumentExecutionInfo);

        verify(dcsOperationHelper, times(1)).returnCompletedExecutionInfo();
        verify(dcsOperationHelper, times(0)).processCheckMaterialStatus(any());
    }

    @Test
    void shouldCompleteAddCourtDocumentTask_WhenDocumentTypeAccessDcsFalse() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String courtDocumentString = getPayload("court-document-defendant-level.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());
        final JsonObject inputdataJsonObject = new StringToJsonObjectConverter().convert(courtDocumentString);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(caseId);
        dcsCaseDetailEntity.setCaseRefId(randomUUID());
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(defendantId);
        dcsCaseDetailEntity.setDefendantRefId(randomUUID());
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByDefendantId(defendantId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(dcsCaseDetailRepository.findByCaseId(caseId)).thenReturn(List.of(dcsCaseDetailEntity));

        when(referenceDataService.getDocumentTypeAccessById(any())).thenReturn(getReferenceDataDocumentTypeWithDcsFalse(documentTypeId.toString()));
        when(clock.now()).thenReturn(ZonedDateTime.now());

        final DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(defendantId);
        dcsDefendantEntity.setMasterDefendantId(defendantId);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);

        final ExecutionInfo addCourtDocumentExecutionInfo = new ExecutionInfo(
                inputdataJsonObject,
                PROCESS_ADD_COURT_DOCUMENT_TASK,
                clock.now().plusSeconds(10),
                STARTED,
                Priority.MEDIUM);

        processAddCourtDocumentTask.execute(addCourtDocumentExecutionInfo);

        verify(dcsOperationHelper, times(1)).returnCompletedExecutionInfo();
        verify(dcsOperationHelper, times(0)).processCheckMaterialStatus(any());
    }

    @Test
    void shouldNotProcessAddCourtDocumentTask_WhenExtensionIsNotAllowed() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String courtDocumentString = getPayload("court-document-defendant-level-zip-extension.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());
        final JsonObject inputdataJsonObject = new StringToJsonObjectConverter().convert(courtDocumentString);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseId(caseId);
        dcsCaseDetailEntity.setCaseRefId(randomUUID());
        dcsCaseDetailEntity.setCaseUrn(random(9));
        dcsCaseDetailEntity.setDefendantId(defendantId);
        dcsCaseDetailEntity.setDefendantRefId(randomUUID());
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.name());
        when(dcsCaseDetailRepository.findByDefendantId(defendantId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(dcsCaseDetailRepository.findByCaseId(caseId)).thenReturn(List.of(dcsCaseDetailEntity));
        when(clock.now()).thenReturn(ZonedDateTime.now());

        final DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(defendantId);
        dcsDefendantEntity.setMasterDefendantId(defendantId);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);

        final ExecutionInfo addCourtDocumentExecutionInfo = new ExecutionInfo(
                inputdataJsonObject,
                PROCESS_ADD_COURT_DOCUMENT_TASK,
                clock.now().plusSeconds(10),
                STARTED,
                Priority.MEDIUM);

        processAddCourtDocumentTask.execute(addCourtDocumentExecutionInfo);

        verify(dcsOperationHelper, times(1)).returnCompletedExecutionInfo();
        verify(dcsOperationHelper, times(0)).processCheckMaterialStatus(any());
    }

    private Optional<JsonObject> getReferenceDataDocumentType(final String documentTypeId){
        final String publicEventPayloadString = getPayload("referencedata.query.document-type-access-by-id.json")
                .replaceAll("DOCUMENT_ACCESS_TYPE_ID", documentTypeId);
        return Optional.of(jsonFromString(publicEventPayloadString));
    }

    private Optional<JsonObject> getReferenceDataDocumentTypeWithDcsFalse(final String documentTypeId){
        final String publicEventPayloadString = getPayload("referencedata.query.document-type-access-by-id-dcs-flag-false.json")
                .replaceAll("DOCUMENT_ACCESS_TYPE_ID", documentTypeId);
        return Optional.of(jsonFromString(publicEventPayloadString));
    }

    private void assertOnMaterialTasData(final MaterialTaskData infoData, final String caseId, final String materialId, final String defendantId) {
        assertThat(infoData.getCaseId(), is(caseId));
        assertThat(infoData.getMaterialId(), is(materialId));
        if(StringUtils.isNotEmpty(defendantId)){
            assertThat(infoData.getDefendantIdReferralIdMap(), notNullValue());
            assertThat(infoData.getDefendantIdReferralIdMap().get(defendantId), notNullValue());
        }
    }
}
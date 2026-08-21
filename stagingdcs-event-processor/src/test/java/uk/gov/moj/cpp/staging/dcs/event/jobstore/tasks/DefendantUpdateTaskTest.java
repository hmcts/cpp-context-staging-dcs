package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.dcs.openapi.model.ErrorResponsePayload.ErrorCodeEnum.CASE_HAS_SPLIT_OR_MERGED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DEFENDANT_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.TRANSACTION_REF;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.FAILED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.RETRY;
import static uk.gov.moj.cpp.staging.dcs.event.util.TestUtil.createProsecutionCaseObject;
import static uk.gov.moj.cpp.staging.dcs.event.util.TestUtil.getDefendant;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.ProsecutionCase;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.ProsecutionCaseIdentifier;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsNotificationHelper;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.ProsecutionCaseHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsRestNotificationService;
import uk.gov.moj.cpp.staging.dcs.event.service.ProgressionService;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsDefendantEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsDefendantRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class DefendantUpdateTaskTest {


    public static final String BAD_REQEUST_RESPONSE = "{\n" +
            "  \"errorMessage\": \"Case ID not valid\"\n" +
            "}";
    public static final String REQUEST_NOT_FOUND_RESPONSE = "{\n" +
            "  \"transactionRef\": \"8191c165-e9e4-4fd7-ac6d-5bfd04690f77\",\n" +
            "  \"errorCode\": \"CASE_NOT_FOUND\",\n" +
            "  \"errorMessage\": \"Case not found\"\n" +
            "}";

    public static final String RESPONSE_CASE_SPLIT_MERGED = "{\n" +
            "  \"transactionRef\": \"8191c165-e9e4-4fd7-ac6d-5bfd04690f77\",\n" +
            "  \"errorCode\": \"CASE_HAS_SPLIT_OR_MERGED\",\n" +
            "  \"errorMessage\": \"case is merged\"\n" +
            "}";
    @InjectMocks
    private DefendantUpdateTask defendantUpdateTask;
    @Mock
    private ProgressionService progressionService;
    @Mock
    private DcsRestNotificationService dcsRestNotificationService;
    @Mock
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;
    @Mock
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;
    @Mock
    private DcsCaseDetailRepository dcsCaseDetailRepository;
    @Mock
    private TransactionDetailRepository transactionDetailRepository;
    @Mock
    private TransactionMetadataRepository transactionMetadataRepository;
    @Mock
    private DcsNotificationHelper dcsNotificationHelper;
    @Mock
    private DcsDefendantRepository dcsDefendantRepository;
    @Mock
    private Logger logger;
    @Mock
    private ProsecutionCaseHelper prosecutionCaseHelper;

    @Mock
    private ExecutionInfo executionInfo;

    @Mock
    private JsonObject jobData;
    @Mock
    private Response response;

    @Mock
    private TransactionDetailEntity transactionDetailEntity;
    @Mock
    private SetFailedStatusTaskFactory setFailedStatusTaskFactory;
    @Mock
    private DcsOperationHelper dcsOperationHelper;

    @Test
    void executeMethodShouldHandlePostSuccess() {

        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(jobData.getString(CASE_ID)).thenReturn(caseId);
        when(jobData.getString(DEFENDANT_ID)).thenReturn(defendantId);
        when(jobData.getString(TRANSACTION_REF)).thenReturn(randomUUID().toString());

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId), UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);

        when(response.getStatus()).thenReturn(HttpStatus.SC_NO_CONTENT);
        when(response.readEntity(String.class)).thenReturn("{}");

        when(dcsRestNotificationService.sendUpdatedDefendantDetails(any(),any(),any())).thenReturn(response);

        Metadata metadata = getMetadata();
        when(jobData.getJsonObject("metadata")).thenReturn(metadata.asJsonObject());

        ProsecutionCase prosecutionCase = new ProsecutionCase(UUID.fromString(caseId), List.of(getDefendant(defendantId)), new ProsecutionCaseIdentifier("XJDKMD"));
        when(prosecutionCaseHelper.getProsecutionCase(any(), any())).thenReturn(prosecutionCase);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(new DcsDefendantEntity());
        defendantUpdateTask.execute(executionInfo);

        verify(dcsNotificationHelper).saveOrUpdateTransactionMetadata(any(), any(), any(), eq(TransactionStatus.SUCCESS.toString()), eq(TransactionType.DEFENDANT_UPDATE), any());
        verify(dcsNotificationHelper).saveOrUpdateTransactionDetails(any(), any(), anyString(), eq(TransactionStatus.SUCCESS.toString()), isNull(), eq(TransactionType.DEFENDANT_UPDATE));
    }
    @Test
    void executeMethodShouldHandleNotFound() {

        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(jobData.getString(CASE_ID)).thenReturn(caseId);
        when(jobData.getString(DEFENDANT_ID)).thenReturn(defendantId);
        when(jobData.getString(TRANSACTION_REF)).thenReturn(randomUUID().toString());

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId), UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);

        when(response.getStatus()).thenReturn(HttpStatus.SC_NOT_FOUND);
        when(response.readEntity(String.class)).thenReturn(REQUEST_NOT_FOUND_RESPONSE);

        when(dcsRestNotificationService.sendUpdatedDefendantDetails(any(),any(),any())).thenReturn(response);

        Metadata metadata = getMetadata();
        when(jobData.getJsonObject("metadata")).thenReturn(metadata.asJsonObject());

        ProsecutionCase prosecutionCase = new ProsecutionCase(UUID.fromString(caseId), List.of(getDefendant(defendantId)), new ProsecutionCaseIdentifier("XJDKMD"));
        when(prosecutionCaseHelper.getProsecutionCase(any(), any())).thenReturn(prosecutionCase);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(new DcsDefendantEntity());
        defendantUpdateTask.execute(executionInfo);

        verify(dcsNotificationHelper).saveOrUpdateTransactionMetadata(any(), eq(UUID.fromString(caseId)), eq(UUID.fromString(defendantId)), eq(FAILED.toString()), eq(TransactionType.DEFENDANT_UPDATE), any());
        verify(dcsNotificationHelper).saveOrUpdateTransactionDetails(any(),any(), any(), eq(FAILED.toString()), eq("CASE_NOT_FOUND: Case not found"), eq(TransactionType.DEFENDANT_UPDATE));
    }

    @Test
    void executeMethodShouldHandle_404CaseSplitAndMerge() {

        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(jobData.getString(CASE_ID)).thenReturn(caseId);
        when(jobData.getString(DEFENDANT_ID)).thenReturn(defendantId);
        when(jobData.getString(TRANSACTION_REF)).thenReturn(randomUUID().toString());

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId), UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);

        when(response.getStatus()).thenReturn(HttpStatus.SC_NOT_FOUND);
        when(response.readEntity(String.class)).thenReturn(RESPONSE_CASE_SPLIT_MERGED);

        when(dcsRestNotificationService.sendUpdatedDefendantDetails(any(),any(),any())).thenReturn(response);

        Metadata metadata = getMetadata();
        when(jobData.getJsonObject("metadata")).thenReturn(metadata.asJsonObject());

        ProsecutionCase prosecutionCase = new ProsecutionCase(UUID.fromString(caseId), List.of(getDefendant(defendantId)), new ProsecutionCaseIdentifier("XJDKMD"));
        when(prosecutionCaseHelper.getProsecutionCase(any(), any())).thenReturn(prosecutionCase);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(new DcsDefendantEntity());
        defendantUpdateTask.execute(executionInfo);

        verify(dcsNotificationHelper).saveOrUpdateTransactionMetadata(any(), eq(UUID.fromString(caseId)), eq(UUID.fromString(defendantId)), eq(FAILED.toString()), eq(TransactionType.DEFENDANT_UPDATE), any());
        verify(dcsNotificationHelper).saveOrUpdateTransactionDetails(any(),any(), any(), eq(FAILED.toString()), eq(CASE_HAS_SPLIT_OR_MERGED.getValue()+": case is merged"), eq(TransactionType.DEFENDANT_UPDATE));
        verify(dcsOperationHelper, times(1)).unlinkByCaseIdIfErrorsPresent(CASE_HAS_SPLIT_OR_MERGED.getValue()+": case is merged", fromString(caseId));
    }

    @Test
    void executeMethodShouldHandleBadRequest() {

        final String defendantId = randomUUID().toString();

        when(executionInfo.getJobData()).thenReturn(jobData);
        final String caseId = randomUUID().toString();
        when(jobData.getString(CASE_ID)).thenReturn(caseId);
        when(jobData.getString(DEFENDANT_ID)).thenReturn(defendantId);
        when(jobData.getString(TRANSACTION_REF)).thenReturn(randomUUID().toString());

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId), UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);

        when(response.getStatus()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(response.readEntity(String.class)).thenReturn(BAD_REQEUST_RESPONSE);

        when(dcsRestNotificationService.sendUpdatedDefendantDetails(any(),any(),any())).thenReturn(response);

        Metadata metadata = getMetadata();
        when(jobData.getJsonObject("metadata")).thenReturn(metadata.asJsonObject());

        ProsecutionCase prosecutionCase = new ProsecutionCase(UUID.fromString(caseId), List.of(getDefendant(defendantId)), new ProsecutionCaseIdentifier("XJDKMD"));
        when(prosecutionCaseHelper.getProsecutionCase(any(), any())).thenReturn(prosecutionCase);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(new DcsDefendantEntity());

        defendantUpdateTask.execute(executionInfo);

        verify(dcsNotificationHelper).saveOrUpdateTransactionMetadata(any(), eq(UUID.fromString(caseId)), eq(UUID.fromString(defendantId)), eq(DcsDefendantStatus.FAILED.toString()), eq(TransactionType.DEFENDANT_UPDATE), any());
        verify(dcsNotificationHelper).saveOrUpdateTransactionDetails(any(), any(), any(), eq(FAILED.toString()), any(), eq(TransactionType.DEFENDANT_UPDATE));
    }

    @Test
    void executeMethodShouldRetry() {

        final String defendantId = randomUUID().toString();

        when(executionInfo.getJobData()).thenReturn(jobData);
        final String caseId = randomUUID().toString();
        when(jobData.getString(CASE_ID)).thenReturn(caseId);
        when(jobData.getString(DEFENDANT_ID)).thenReturn(defendantId);
        when(jobData.getString(TRANSACTION_REF)).thenReturn(randomUUID().toString());

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId), UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);

        when(response.getStatus()).thenReturn(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        when(response.readEntity(String.class)).thenReturn(BAD_REQEUST_RESPONSE);

        when(dcsRestNotificationService.sendUpdatedDefendantDetails(any(),any(),any())).thenReturn(response);

        Metadata metadata = getMetadata();
        when(jobData.getJsonObject("metadata")).thenReturn(metadata.asJsonObject());

        ProsecutionCase prosecutionCase = new ProsecutionCase(UUID.fromString(caseId), List.of(getDefendant(defendantId)), new ProsecutionCaseIdentifier("XJDKMD"));
        when(prosecutionCaseHelper.getProsecutionCase(any(), any())).thenReturn(prosecutionCase);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(new DcsDefendantEntity());

        defendantUpdateTask.execute(executionInfo);

        verify(dcsNotificationHelper).saveOrUpdateTransactionMetadata(any(), eq(UUID.fromString(caseId)), eq(UUID.fromString(defendantId)), eq(RETRY.toString()), eq(TransactionType.DEFENDANT_UPDATE), any());
        verify(dcsNotificationHelper).saveOrUpdateTransactionDetails(any(), any(), any(), eq(RETRY.toString()), any(), eq(TransactionType.DEFENDANT_UPDATE));
    }

    private static Metadata getMetadata() {
        return JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName("test")
                .withUserId(randomUUID().toString())
                .build();
    }

    @Test
    void executeMethodShouldNotSendUpdate() {

        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String tranRefId = randomUUID().toString();
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(jobData.getString(CASE_ID)).thenReturn(caseId);
        when(jobData.getString(DEFENDANT_ID)).thenReturn(defendantId);
        when(jobData.getString(TRANSACTION_REF)).thenReturn(tranRefId);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        DcsDefendantEntity dcsDefendantEntity =  new DcsDefendantEntity();
        dcsDefendantEntity.setDefendantId(UUID.fromString(defendantId));
        dcsDefendantEntity.setForename("DummyFirstName");
        dcsDefendantEntity.setMiddlename("DummyMiddleName");
        dcsDefendantEntity.setSurname("DummyLastName");
        dcsDefendantEntity.setDateOfBirth(LocalDate.parse("1980-01-01"));

        Metadata metadata = getMetadata();
        when(jobData.getJsonObject("metadata")).thenReturn(metadata.asJsonObject());

        final JsonObject prosecutionCaseObject = createProsecutionCaseObject(caseId, defendantId);

        ProsecutionCase prosecutionCase = new ProsecutionCase(UUID.fromString(caseId), List.of(getDefendant(defendantId)), new ProsecutionCaseIdentifier("XJDKMD"));
        when(prosecutionCaseHelper.getProsecutionCase(any(), any())).thenReturn(prosecutionCase);
        when(dcsDefendantRepository.findByDefendantId(any())).thenReturn(dcsDefendantEntity);
        defendantUpdateTask.execute(executionInfo);
        verify(dcsCaseDetailRepository, times(0)).findByCaseIdDefendantId(any(), any());

    }

}
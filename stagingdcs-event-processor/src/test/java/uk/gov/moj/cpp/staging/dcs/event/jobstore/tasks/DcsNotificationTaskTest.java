package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_URN;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CREATE_PAYLOAD;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.ERROR_CODE;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.DEFENCE_REPRESENTATION_TASK;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.INITIATE_MATERIAL_TASK_FOR_CASE;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.TRANSACTION_REF;

import uk.gov.hmcts.dcs.openapi.model.Defendant;
import uk.gov.hmcts.dcs.openapi.model.ErrorResponsePayload;
import uk.gov.hmcts.dcs.openapi.model.LinkCaseAndDefendantRequest;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsNotificationHelper;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.ProsecutionCaseHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsRestNotificationService;
import uk.gov.moj.cpp.staging.dcs.event.service.DefenceService;
import uk.gov.moj.cpp.staging.dcs.event.service.LinkCaseAndDefendantRequestConverter;
import uk.gov.moj.cpp.staging.dcs.event.service.RestEasyClientService;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class DcsNotificationTaskTest {
    @InjectMocks
    private DcsNotificationTask dcsNotificationTask;
    @Mock
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;
    @Mock
    private DcsRestNotificationService dcsRestNotificationService;
    @Mock
    private RestEasyClientService restEasyClientService;
    @Mock
    private Logger logger;
    @Mock
    private SetFailedStatusTaskFactory setFailedStatusTaskFactory;
    @Mock
    private TransactionDetailRepository transactionDetailRepository;
    @Mock
    private TransactionMetadataRepository transactionMetadataRepository;
    @Mock
    private Response apiResponse;
    @Mock
    private StringToJsonObjectConverter stringToJsonObjectConverter;
    @Mock
    private UtcClock clock;
    @Mock
    private ExecutionService executionService;
    @Mock
    private DcsCaseDetailRepository dcsCaseDetailRepository;
    @Mock
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;
    @Mock
    private DcsNotificationHelper dcsNotificationHelper;
    @Mock
    private LinkCaseAndDefendantRequestConverter linkCaseAndDefendantRequestConverter;
    @Mock
    private DefenceService defenceService;

    @Mock
    private DcsOperationHelper dcsOperationHelper;

    @Mock
    private ProsecutionCaseHelper prosecutionCaseHelper;

    @Test
    void shouldProcessDcsCaseDefendantDetailsWhenResponse201(){
        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        final JsonObject jobData = mock(JsonObject.class);
        final Response response = mock(Response.class);

        LinkCaseAndDefendantRequest linkCaseAndDefendantRequest = new LinkCaseAndDefendantRequest();
        linkCaseAndDefendantRequest.setCaseId(randomUUID().toString());
        linkCaseAndDefendantRequest.setTransactionRef(randomUUID().toString());

        final Defendant defendant = new Defendant();
        defendant.setId("d9fe4c51-1783-4fe4-a2b4-7c0e25905484");
        linkCaseAndDefendantRequest.setDefendants(Arrays.asList(defendant));

        final JsonObject jobDataLinkCaseAndDefendantRequest = mock(JsonObject.class);

        when(executionInfo.getJobData()).thenReturn(jobData);
        when(jobData.getString(CASE_URN)).thenReturn(randomUUID().toString());

        when(jobData.getJsonObject(CREATE_PAYLOAD)).thenReturn(jobDataLinkCaseAndDefendantRequest);
        when(jobData.getString(TRANSACTION_REF)).thenReturn(randomUUID().toString());

        Metadata metadata = getMetadata();
        when(jobData.getJsonObject("metadata")).thenReturn(metadata.asJsonObject());

        final String responseBody = "{\n" +
                "  \"transactionRef\": \"d9fe4c51-1783-4fe4-a2b4-7c0e25905483\",\n" +
                "  \"caseId\": \"d9fe4c51-1783-4fe4-a2b4-7c0e25905484\",\n" +
                "  \"caseReferral\": \"d9fe4c51-1783-4fe4-a2b4-7c0e25905484\",\n" +
                "  \"defendants\": [\n" +
                "    {\n" +
                "      \"defendantId\": \"d9fe4c51-1783-4fe4-a2b4-7c0e25905484\",\n" +
                "      \"defendantReferral\": \"d9fe4c51-1783-4fe4-a2b4-7c0e25905484\"\n" +
                "    }\n" +
                "  ]" +
                "}";
        when(response.readEntity(String.class)).thenReturn(responseBody);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        when(linkCaseAndDefendantRequestConverter.convert(any(), any())).thenReturn(linkCaseAndDefendantRequest);
        when(dcsRestNotificationService.submitCaseAndDefendantDetails(any(),any())).thenReturn(response);

        final JsonObject associationObject = createObjectBuilder().add("association", createObjectBuilder()
                .add("organisationId", randomUUID().toString()).build()).build();

        when(defenceService.getAssociatedOrganisation(any(), any())).thenReturn(Optional.ofNullable(associationObject));

        when(response.getStatus()).thenReturn(201);
        final ArgumentCaptor<ExecutionInfo> argumentCaptor = ArgumentCaptor.forClass(ExecutionInfo.class);

        ExecutionInfo outputExecutionInfo = dcsNotificationTask.execute(executionInfo);

        assertThat(outputExecutionInfo.getExecutionStatus(), is(COMPLETED));
        verify(dcsRestNotificationService, times(1)).submitCaseAndDefendantDetails(any(), any());
        verify(executionService, times(1)).executeWith(argumentCaptor.capture());
        verify(dcsOperationHelper, times(1)).initiateMaterialTaskForCase(any());
        final List<ExecutionInfo> executionInfoList = argumentCaptor.getAllValues();
        executionInfoList.forEach(execution -> assertThat(execution.getNextTask(), anyOf(is(DEFENCE_REPRESENTATION_TASK), is(INITIATE_MATERIAL_TASK_FOR_CASE))));

    }

    @Test
    void shouldProcessDcsCaseDefendantDetailsWhenResponse400(){
        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        final JsonObject jobData = mock(JsonObject.class);
        final Response response = mock(Response.class);

        LinkCaseAndDefendantRequest linkCaseAndDefendantRequest = new LinkCaseAndDefendantRequest();
        linkCaseAndDefendantRequest.setCaseId(randomUUID().toString());
        linkCaseAndDefendantRequest.setTransactionRef(randomUUID().toString());

        final Defendant defendant = new Defendant();
        defendant.setId("d9fe4c51-1783-4fe4-a2b4-7c0e25905484");
        linkCaseAndDefendantRequest.setDefendants(Arrays.asList(defendant));

        final JsonObject jobDataLinkCaseAndDefendantRequest = mock(JsonObject.class);

        when(executionInfo.getJobData()).thenReturn(jobData);
        when(jobData.getString(CASE_URN)).thenReturn(randomUUID().toString());
        when(jobData.getString(TRANSACTION_REF)).thenReturn(randomUUID().toString());
        when(jobData.getJsonObject(CREATE_PAYLOAD)).thenReturn(jobDataLinkCaseAndDefendantRequest);

        final String responseBody = "{\n" +
                "  \"errorMessage\": \"Bad Request\"" +
                "}";
        when(response.readEntity(String.class)).thenReturn(responseBody);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(any(UUID.class),any(UUID.class))).thenReturn(dcsCaseDetailEntity);

        when(linkCaseAndDefendantRequestConverter.convert(any(), any())).thenReturn(linkCaseAndDefendantRequest);
        when(dcsRestNotificationService.submitCaseAndDefendantDetails(any(),any())).thenReturn(response);
        when(response.getStatus()).thenReturn(400);
        ExecutionInfo outputExecutionInfo = dcsNotificationTask.execute(executionInfo);

        assertThat(outputExecutionInfo.getExecutionStatus(), is(COMPLETED));
        verify(dcsRestNotificationService, times(1)).submitCaseAndDefendantDetails(any(), any());
        verify(executionService, times(0)).executeWith(any());
    }

    @Test
    void shouldProcessDcsCaseDefendantDetailsWhenResponse404SplitAndMerge() {
        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        final JsonObject jobData = mock(JsonObject.class);
        final Response response = mock(Response.class);

        final UUID caseId = randomUUID();
        LinkCaseAndDefendantRequest linkCaseAndDefendantRequest = new LinkCaseAndDefendantRequest();
        linkCaseAndDefendantRequest.setCaseId(caseId.toString());
        linkCaseAndDefendantRequest.setTransactionRef(randomUUID().toString());

        final Defendant defendant = new Defendant();
        defendant.setId("d9fe4c51-1783-4fe4-a2b4-7c0e25905484");
        linkCaseAndDefendantRequest.setDefendants(Arrays.asList(defendant));

        final JsonObject jobDataLinkCaseAndDefendantRequest = mock(JsonObject.class);

        when(executionInfo.getJobData()).thenReturn(jobData);
        when(jobData.getString(CASE_URN)).thenReturn(randomUUID().toString());
        when(jobData.getString(TRANSACTION_REF)).thenReturn(randomUUID().toString());
        when(jobData.getJsonObject(CREATE_PAYLOAD)).thenReturn(jobDataLinkCaseAndDefendantRequest);

        final String caseMergeMessage = "case had be merged";
        final String responseBody = createObjectBuilder()
                .add(ERROR_MESSAGE, caseMergeMessage)
                .add(ERROR_CODE, ErrorResponsePayload.ErrorCodeEnum.CASE_HAS_SPLIT_OR_MERGED.getValue())
                .build().toString();

        when(response.readEntity(String.class)).thenReturn(responseBody);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(any(UUID.class), any(UUID.class))).thenReturn(dcsCaseDetailEntity);

        when(linkCaseAndDefendantRequestConverter.convert(any(), any())).thenReturn(linkCaseAndDefendantRequest);
        when(dcsRestNotificationService.submitCaseAndDefendantDetails(any(), any())).thenReturn(response);
        when(response.getStatus()).thenReturn(400);
        ExecutionInfo outputExecutionInfo = dcsNotificationTask.execute(executionInfo);

        assertThat(outputExecutionInfo.getExecutionStatus(), is(COMPLETED));
        verify(dcsRestNotificationService, times(1)).submitCaseAndDefendantDetails(any(), any());
        verify(executionService, times(0)).executeWith(any());
        verify(dcsOperationHelper, times(1)).unlinkByCaseIdIfErrorsPresent(caseMergeMessage, fromString(caseId.toString()));
    }

    @Test
    void shouldProcessDcsCaseDefendantDetailsWhenResponse500(){
        final ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        final JsonObject jobData = mock(JsonObject.class);
        final Response response = mock(Response.class);
        final TransactionDetailEntity transactionDetailEntity = mock(TransactionDetailEntity.class);

        LinkCaseAndDefendantRequest linkCaseAndDefendantRequest = new LinkCaseAndDefendantRequest();
        linkCaseAndDefendantRequest.setCaseId(randomUUID().toString());
        linkCaseAndDefendantRequest.setTransactionRef(randomUUID().toString());

        final Defendant defendant = new Defendant();
        defendant.setId(randomUUID().toString());
        linkCaseAndDefendantRequest.setDefendants(Arrays.asList(defendant));

        final JsonObject jobDataLinkCaseAndDefendantRequest = mock(JsonObject.class);

        when(executionInfo.getJobData()).thenReturn(jobData);
        when(jobData.getString(CASE_URN)).thenReturn(randomUUID().toString());
        when(jobData.getString(TRANSACTION_REF)).thenReturn(randomUUID().toString());
        when(jobData.getJsonObject(CREATE_PAYLOAD)).thenReturn(jobDataLinkCaseAndDefendantRequest);

        final String responseBody = "{\n" +
                "  \"errorMessage\": \"Bad Request\"" +
                "}";
        when(response.readEntity(String.class)).thenReturn(responseBody);

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        when(linkCaseAndDefendantRequestConverter.convert(any(), any())).thenReturn(linkCaseAndDefendantRequest);
        when(dcsRestNotificationService.submitCaseAndDefendantDetails(any(),any())).thenReturn(response);
        when(response.getStatus()).thenReturn(500);
        when(transactionDetailRepository.findByTransactionReferenceId(any())).thenReturn(transactionDetailEntity);
        when(transactionDetailEntity.getTransactionRefId()).thenReturn(null);
        ExecutionInfo outputExecutionInfo = dcsNotificationTask.execute(executionInfo);

        verify(dcsRestNotificationService, times(1)).submitCaseAndDefendantDetails(any(), any());
        verify(executionService, times(0)).executeWith(any());
    }

    private static Metadata getMetadata() {
        return JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName("test")
                .withUserId(randomUUID().toString())
                .build();
    }
}
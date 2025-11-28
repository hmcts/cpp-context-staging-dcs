package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.dcs.openapi.model.ErrorResponsePayload.ErrorCodeEnum.CASE_HAS_SPLIT_OR_MERGED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.ACTION;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DEFENDANT_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.ORGANISATION_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.TRANSACTION_REF;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.FAILED;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.DefenceOrganisation;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsNotificationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsRestNotificationService;
import uk.gov.moj.cpp.staging.dcs.event.service.UserGroupService;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsDefendantRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.core.Response;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class DefenceRepresentationTaskTest {

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
    private DefenceRepresentationTask defenceRepresentationTask;
    @Mock
    private UserGroupService userGroupService;
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
    private Logger logger;

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
    private DcsDefendantRepository dcsDefendantRepository;

    @Mock
    private DcsOperationHelper dcsOperationHelper;

    @Test
    void executeMethodShouldHandlePostSuccess() {

        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(jobData.getString(CASE_ID)).thenReturn(caseId);
        when(jobData.getString(DEFENDANT_ID)).thenReturn(defendantId);
        when(jobData.getString(ORGANISATION_ID)).thenReturn(randomUUID().toString());
        when(jobData.getString(ACTION)).thenReturn("CREATE");
        when(jobData.getString(TRANSACTION_REF)).thenReturn(randomUUID().toString());

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId), UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);

        when(response.getStatus()).thenReturn(HttpStatus.SC_NO_CONTENT);
        when(response.readEntity(String.class)).thenReturn("{}");

        when(dcsRestNotificationService.sendUpdatedDefenceRepresentationDetails(any(),any(),any())).thenReturn(response);

        Metadata metadata = getMetadata();
        when(jobData.getJsonObject("metadata")).thenReturn(metadata.asJsonObject());

        JsonObject organisatonbject = createObjectBuilder()
                .add("name", "test ltd")
                .add("email", "test@test.com")
                .build();
        DefenceOrganisation organisation = new DefenceOrganisation("test ltd", "test@test.com");
        when(userGroupService.getOrganisationDetails(any(), any())).thenReturn(organisatonbject);

        when(jsonObjectToObjectConverter.convert(organisatonbject, DefenceOrganisation.class)).thenReturn(organisation);
        defenceRepresentationTask.execute(executionInfo);

        verify(dcsNotificationHelper).saveOrUpdateTransactionMetadata(any(), any(), any(), eq(TransactionStatus.SUCCESS.toString()), eq(TransactionType.DEFENCE_REPRESENTATION), any());
        verify(dcsNotificationHelper).saveOrUpdateTransactionDetails(any(), any(), anyString(), eq(TransactionStatus.SUCCESS.toString()), isNull(), eq(TransactionType.DEFENCE_REPRESENTATION));
    }

    @Test
    void executeMethodShouldHandleNotFound() {

        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(jobData.getString(CASE_ID)).thenReturn(caseId);
        when(jobData.getString(DEFENDANT_ID)).thenReturn(defendantId);
        when(jobData.getString(ORGANISATION_ID)).thenReturn(randomUUID().toString());
        when(jobData.getString(ACTION)).thenReturn("CREATE");
        when(jobData.getString(TRANSACTION_REF)).thenReturn(randomUUID().toString());

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseRefId(randomUUID());

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId), UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);

        when(response.getStatus()).thenReturn(HttpStatus.SC_NOT_FOUND);
        when(response.readEntity(String.class)).thenReturn(REQUEST_NOT_FOUND_RESPONSE);

        when(dcsRestNotificationService.sendUpdatedDefenceRepresentationDetails(any(),any(),any())).thenReturn(response);

        Metadata metadata = getMetadata();
        when(jobData.getJsonObject("metadata")).thenReturn(metadata.asJsonObject());

        JsonObject organisatonbject = createObjectBuilder()
                .add("name", "test ltd")
                .add("email", "test@test.com")
                .build();
        DefenceOrganisation organisation = new DefenceOrganisation("test ltd", "test@test.com");
        when(userGroupService.getOrganisationDetails(any(), any())).thenReturn(organisatonbject);
        when(jsonObjectToObjectConverter.convert(organisatonbject, DefenceOrganisation.class)).thenReturn(organisation);
        defenceRepresentationTask.execute(executionInfo);

        verify(dcsNotificationHelper).saveOrUpdateTransactionMetadata(any(), eq(UUID.fromString(caseId)), eq(UUID.fromString(defendantId)), eq(FAILED.toString()), eq(TransactionType.DEFENCE_REPRESENTATION), any());
        verify(dcsNotificationHelper).saveOrUpdateTransactionDetails(any(),any(), any(), eq(FAILED.toString()), eq("CASE_NOT_FOUND: Case not found"), eq(TransactionType.DEFENCE_REPRESENTATION));
    }

    @Test
    void executeMethodShouldHandle_404ErrorCaseSplitAndMerged() {

        final String defendantId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(jobData.getString(CASE_ID)).thenReturn(caseId);
        when(jobData.getString(DEFENDANT_ID)).thenReturn(defendantId);
        when(jobData.getString(ORGANISATION_ID)).thenReturn(randomUUID().toString());
        when(jobData.getString(ACTION)).thenReturn("CREATE");
        when(jobData.getString(TRANSACTION_REF)).thenReturn(randomUUID().toString());

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId), UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);

        when(response.getStatus()).thenReturn(HttpStatus.SC_NOT_FOUND);
        when(response.readEntity(String.class)).thenReturn(RESPONSE_CASE_SPLIT_MERGED);

        when(dcsRestNotificationService.sendUpdatedDefenceRepresentationDetails(any(),any(),any())).thenReturn(response);

        Metadata metadata = getMetadata();
        when(jobData.getJsonObject("metadata")).thenReturn(metadata.asJsonObject());

        JsonObject organisatonbject = createObjectBuilder()
                .add("name", "test ltd")
                .add("email", "test@test.com")
                .build();
        DefenceOrganisation organisation = new DefenceOrganisation("test ltd", "test@test.com");
        when(userGroupService.getOrganisationDetails(any(), any())).thenReturn(organisatonbject);
        when(jsonObjectToObjectConverter.convert(organisatonbject, DefenceOrganisation.class)).thenReturn(organisation);
        defenceRepresentationTask.execute(executionInfo);

        verify(dcsNotificationHelper).saveOrUpdateTransactionMetadata(any(), eq(UUID.fromString(caseId)), eq(UUID.fromString(defendantId)), eq(FAILED.toString()), eq(TransactionType.DEFENCE_REPRESENTATION), any());
        verify(dcsNotificationHelper).saveOrUpdateTransactionDetails(any(),any(), any(), eq(FAILED.toString()), eq(CASE_HAS_SPLIT_OR_MERGED.getValue()+": case is merged"), eq(TransactionType.DEFENCE_REPRESENTATION));
        verify(dcsOperationHelper, times(1)).unlinkByCaseIdIfErrorsPresent(CASE_HAS_SPLIT_OR_MERGED.getValue()+": case is merged", fromString(caseId));
    }

    @Test
    void executeMethodShouldHandleBadRequest() {

        final String defendantId = randomUUID().toString();

        when(executionInfo.getJobData()).thenReturn(jobData);
        final String caseId = randomUUID().toString();
        when(jobData.getString(CASE_ID)).thenReturn(caseId);
        when(jobData.getString(DEFENDANT_ID)).thenReturn(defendantId);
        when(jobData.getString(ORGANISATION_ID)).thenReturn(randomUUID().toString());
        when(jobData.getString(ACTION)).thenReturn("CREATE");
        when(jobData.getString(TRANSACTION_REF)).thenReturn(randomUUID().toString());

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(UUID.fromString(caseId), UUID.fromString(defendantId))).thenReturn(dcsCaseDetailEntity);

        when(response.getStatus()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(response.readEntity(String.class)).thenReturn(BAD_REQEUST_RESPONSE);

        when(dcsRestNotificationService.sendUpdatedDefenceRepresentationDetails(any(),any(),any())).thenReturn(response);

        Metadata metadata = getMetadata();
        when(jobData.getJsonObject("metadata")).thenReturn(metadata.asJsonObject());

        JsonObject organisatonbject = createObjectBuilder()
                .add("name", "test ltd")
                .add("email", "test@test.com")
                .build();
        DefenceOrganisation organisation = new DefenceOrganisation("test ltd", "test@test.com");
        when(userGroupService.getOrganisationDetails(any(), any())).thenReturn(organisatonbject);


        when(jsonObjectToObjectConverter.convert(organisatonbject, DefenceOrganisation.class)).thenReturn(organisation);

        defenceRepresentationTask.execute(executionInfo);

        verify(dcsNotificationHelper).saveOrUpdateTransactionMetadata(any(), eq(UUID.fromString(caseId)), eq(UUID.fromString(defendantId)), eq(DcsDefendantStatus.FAILED.toString()), eq(TransactionType.DEFENCE_REPRESENTATION), any());
        verify(dcsNotificationHelper).saveOrUpdateTransactionDetails(any(), any(), any(), eq(TransactionStatus.FAILED.toString()), any(), eq(TransactionType.DEFENCE_REPRESENTATION));
    }

    private static Metadata getMetadata() {
        return JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName("test")
                .withUserId(randomUUID().toString())
                .build();
    }
}
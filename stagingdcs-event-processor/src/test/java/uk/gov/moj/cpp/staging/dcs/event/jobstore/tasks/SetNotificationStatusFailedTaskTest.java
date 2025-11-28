package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.DCS_NOTIFICATION_TASK;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.jobdata.SetNotificationStatusFailedJobData;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionMetadataEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class SetNotificationStatusFailedTaskTest {

    @InjectMocks
    private SetNotificationStatusFailedTask setNotificationStatusFailedTask;
    @Mock
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;
    @Mock
    private Logger logger;
    @Mock
    private TransactionDetailRepository transactionDetailRepository;
    @Mock
    private TransactionMetadataRepository transactionMetadataRepository;
    @Mock
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Test
    void shouldUpdateNotificationStatusAndNowDocumentRequestStatusToFailedAndCompleteJob_Email() {
        ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        UUID transactionReference = randomUUID();
        String task = DCS_NOTIFICATION_TASK;
        String errorMessage = "Error message";

        SetNotificationStatusFailedJobData setNotificationStatusFailedJobData = new SetNotificationStatusFailedJobData(transactionReference, task, errorMessage);
        when(jsonObjectToObjectConverter.convert(executionInfo.getJobData(), SetNotificationStatusFailedJobData.class)).thenReturn(setNotificationStatusFailedJobData);

        ExecutionInfo result = setNotificationStatusFailedTask.execute(executionInfo);

        verify(transactionDetailRepository).updateStatusByTransactionReferenceId(any(), any(), any());
        verify(transactionMetadataRepository).updateStatusByTransactionReferenceId(any(), any());
        verify(dcsCaseDetailRepository, times(0)).findByCaseIdDefendantId(any(), any());
        verify(dcsCaseDetailRepository, times(0)).updateStatusById(any(), any());
        verify(logger).warn("Exhausted retries for {} for transactionReference: {}", task, transactionReference);
        assertEquals(COMPLETED, result.getExecutionStatus());
    }

    @Test
    void shouldUpdate_DcsCase_TransactionMetaData_TransactionDetails_OnRetryFailure() {
        ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        UUID transactionReference = randomUUID();
        String task = DCS_NOTIFICATION_TASK;
        String errorMessage = "Error message";

        SetNotificationStatusFailedJobData setNotificationStatusFailedJobData = new SetNotificationStatusFailedJobData(transactionReference, task, errorMessage);
        when(jsonObjectToObjectConverter.convert(executionInfo.getJobData(), SetNotificationStatusFailedJobData.class)).thenReturn(setNotificationStatusFailedJobData);
        TransactionMetadataEntity entity = new TransactionMetadataEntity();
        entity.setTransactionRefId(transactionReference);
        entity.setTransactionType(TransactionType.LINK_DEFENDANT.name());
        when(transactionMetadataRepository.findByTransactionReferenceId(any())).thenReturn(List.of(entity));
        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setCaseId(randomUUID());
        when(dcsCaseDetailRepository.findByCaseIdDefendantId(any(),any())).thenReturn(dcsCaseDetailEntity);

        ExecutionInfo result = setNotificationStatusFailedTask.execute(executionInfo);

        verify(transactionDetailRepository).updateStatusByTransactionReferenceId(any(), any(), any());
        verify(transactionMetadataRepository).updateStatusByTransactionReferenceId(any(), any());
        verify(dcsCaseDetailRepository, times(1)).findByCaseIdDefendantId(any(), any());
        verify(dcsCaseDetailRepository, times(1)).updateStatusById(any(), any());
        verify(logger).warn("Exhausted retries for {} for transactionReference: {}", task, transactionReference);
        assertEquals(COMPLETED, result.getExecutionStatus());
    }
}

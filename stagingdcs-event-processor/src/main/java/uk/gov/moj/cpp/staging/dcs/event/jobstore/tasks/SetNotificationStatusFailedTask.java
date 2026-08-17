package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.SET_NOTIFICATION_STATUS_FAILED_TASK;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.moj.cpp.jobstore.api.annotation.Task;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.jobdata.SetNotificationStatusFailedJobData;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionMetadataEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.slf4j.Logger;

@Task(SET_NOTIFICATION_STATUS_FAILED_TASK)
public class SetNotificationStatusFailedTask implements ExecutableTask {

    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;
    @Inject
    private Logger logger;
    @Inject
    private DcsCaseDetailRepository dcsCaseDetailRepository;
    @Inject
    private TransactionDetailRepository transactionDetailRepository;
    @Inject
    private TransactionMetadataRepository transactionMetadataRepository;

    @Override
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {
        final SetNotificationStatusFailedJobData setNotificationStatusFailedJobData = jsonObjectToObjectConverter.convert(
                executionInfo.getJobData(),
                SetNotificationStatusFailedJobData.class);
        final UUID transactionReference = setNotificationStatusFailedJobData.transactionReference();
        final String task = setNotificationStatusFailedJobData.task();
        final String errorMessage = setNotificationStatusFailedJobData.errorMessage();

        final List<TransactionMetadataEntity> metadataEntities = transactionMetadataRepository.findByTransactionReferenceId(transactionReference);
        metadataEntities.stream()
                .filter(transactionMetadataEntity -> TransactionType.LINK_DEFENDANT.name().equalsIgnoreCase(transactionMetadataEntity.getTransactionType()))
                .forEach(transactionMetadataEntity -> {
            final DcsCaseDetailEntity dcsCaseDetailEntity = dcsCaseDetailRepository.findByCaseIdDefendantId(transactionMetadataEntity.getCaseId(), transactionMetadataEntity.getDefendantId());
            dcsCaseDetailRepository.updateStatusById(DcsDefendantStatus.FAILED.toString(), dcsCaseDetailEntity.getId());
        });

        transactionDetailRepository.updateStatusByTransactionReferenceId(TransactionStatus.FAILED.toString(), errorMessage, transactionReference);
        transactionMetadataRepository.updateStatusByTransactionReferenceId(TransactionStatus.FAILED.toString(), transactionReference);
       logger.warn("Exhausted retries for {} for transactionReference: {}", task, transactionReference);

        return executionInfo()
                .withExecutionStatus(COMPLETED)
                .build();
    }
}

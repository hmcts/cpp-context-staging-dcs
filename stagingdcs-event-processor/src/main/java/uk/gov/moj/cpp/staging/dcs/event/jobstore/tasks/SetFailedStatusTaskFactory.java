package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.INPROGRESS;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.SET_NOTIFICATION_STATUS_FAILED_TASK;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.jobdata.SetNotificationStatusFailedJobData;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.inject.Inject;

public class SetFailedStatusTaskFactory {

    @Inject
    private UtcClock clock;
    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    public ExecutionInfo createRetryWithSetNotificationStatusFailedTaskOnExhaust(
            final UUID transactionReference,
            final String task,
            final String errorMessage) {

        final ZonedDateTime failedTime = clock.now();

        return createRetryWithSetNotificationStatusFailedTaskOnExhaustWithNextRetryTime(transactionReference,
                task,
                errorMessage,
                failedTime);
    }

    public ExecutionInfo createRetryWithSetNotificationStatusFailedTaskOnExhaustWithNextRetryTime(
            final UUID transactionReference,
            final String task,
            final String errorMessage,
            final ZonedDateTime nextStartTime) {

        final SetNotificationStatusFailedJobData setNotificationStatusFailedJobData = new SetNotificationStatusFailedJobData(
                transactionReference,
                task,
                errorMessage
        );

        return executionInfo()
                .withShouldRetry(true)
                .withNextTask(SET_NOTIFICATION_STATUS_FAILED_TASK)
                .withJobData(objectToJsonObjectConverter.convert(setNotificationStatusFailedJobData))
                .withExecutionStatus(INPROGRESS)
                .withNextTaskStartTime(nextStartTime)
                .withPriority(Priority.MEDIUM)
                .build();
    }
}

package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.INPROGRESS;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.SET_NOTIFICATION_STATUS_FAILED_TASK;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.jobdata.SetNotificationStatusFailedJobData;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SetFailedStatusTaskFactoryTest {

    private static final String TRANSACTION_REFERENCE = "transactionReference";
    private static final String TASK_NAME = "taskName";
    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String taskName = "stagingdcs-name-of-task";
    private static final String errorMessageString = "This is an error";

    @InjectMocks
    private SetFailedStatusTaskFactory setFailedStatusTaskFactory;
    @Mock
    private UtcClock clock;
    @Mock
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;
    @Captor
    private ArgumentCaptor<SetNotificationStatusFailedJobData> setNotificationStatusFailedJobDataArgumentCaptor;

    @Test
    void shouldCreateRetryWithSetNotificationStatusFailedTaskOnExhaust() {

        final UUID transactionReference = randomUUID();
        final ZonedDateTime failedTime = new UtcClock().now();
        final JsonObject jobData = createObjectBuilder()
                .add(TRANSACTION_REFERENCE, transactionReference.toString())
                .add(TASK_NAME, taskName)
                .add(ERROR_MESSAGE, errorMessageString)
                .build();

        when(objectToJsonObjectConverter.convert(any(SetNotificationStatusFailedJobData.class))).thenReturn(jobData);
        when(clock.now()).thenReturn(failedTime);

        final ExecutionInfo nextTaskExecutionInfo = setFailedStatusTaskFactory.createRetryWithSetNotificationStatusFailedTaskOnExhaust(
                transactionReference,
                taskName,
                errorMessageString);

        assertThat(nextTaskExecutionInfo.getNextTask(), is(SET_NOTIFICATION_STATUS_FAILED_TASK));
        assertThat(nextTaskExecutionInfo.getExecutionStatus(), is(INPROGRESS));
        assertThat(nextTaskExecutionInfo.getJobData(), is(jobData));
        assertThat(nextTaskExecutionInfo.getNextTaskStartTime(), is(failedTime));
        assertThat(nextTaskExecutionInfo.isShouldRetry(), is(true));
        assertThat(nextTaskExecutionInfo.getPriority(), is(Priority.MEDIUM));

        verify(objectToJsonObjectConverter).convert(setNotificationStatusFailedJobDataArgumentCaptor.capture());
        final SetNotificationStatusFailedJobData setNotificationStatusFailedJobData = setNotificationStatusFailedJobDataArgumentCaptor.getValue();
        assertThat(setNotificationStatusFailedJobData.transactionReference(), is(transactionReference));
        assertThat(setNotificationStatusFailedJobData.task(), is(taskName));
        assertThat(setNotificationStatusFailedJobData.errorMessage(), is(errorMessageString));
    }
}
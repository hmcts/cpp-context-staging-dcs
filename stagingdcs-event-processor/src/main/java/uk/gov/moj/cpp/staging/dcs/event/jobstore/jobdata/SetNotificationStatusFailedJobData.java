package uk.gov.moj.cpp.staging.dcs.event.jobstore.jobdata;

import java.util.UUID;

public record SetNotificationStatusFailedJobData(UUID transactionReference, String task, String errorMessage) {
}

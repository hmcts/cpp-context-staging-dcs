package uk.gov.moj.cpp.staging.dcs.event.processor;

import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_URN;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.DCS_NOTIFICATION_TASK;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.jobdata.DcsCaseRequestJobData;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import org.slf4j.Logger;

public class DcsCaseRequestProcessor {
    @Inject
    private Logger logger;
    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;
    @Inject
    private UtcClock clock;
    @Inject
    private ExecutionService executionService;

    public void processDcsCaseRequestHandler(final JsonObject createPayload, final Metadata metadata) {
        logger.info("Process DCS Case create Request --------------");
        final String caseUrn = createPayload.getString(CASE_URN);

        final DcsCaseRequestJobData dcsCaseRequestJobData = new DcsCaseRequestJobData(createPayload, caseUrn, metadata.asJsonObject(), UUID.randomUUID().toString());

        final ExecutionInfo executionInfo = new ExecutionInfo(
                objectToJsonObjectConverter.convert(dcsCaseRequestJobData),
                DCS_NOTIFICATION_TASK,
                clock.now(),
                STARTED,
                Priority.MEDIUM);

        executionService.executeWith(executionInfo);
        logger.info("Added DCS_NOTIFICATION_TASK to jobstore for caseId {}", createPayload.getString("caseId"));
    }
}

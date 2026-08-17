package uk.gov.moj.cpp.staging.dcs.event.processor;

import static java.util.UUID.fromString;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.FEATURE_STAGING_DCS;
import static uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus.LINKED;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.DEFENDANT_UPDATE_TASK;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.JsonMetadata;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.jobdata.DcsUpdateJobData;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;

import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import org.slf4j.Logger;

@ServiceComponent(EVENT_PROCESSOR)
public class ProgressionEventProcessor {

    public static final String PROSECUTION_CASE_ID = "prosecutionCaseId";
    public static final String ID = "id";
    public static final String DEFENDANT = "defendant";
    public static final String INFO_LOG_FOR_FEATURE_STAGING_DCS_DISABLED = "Feature StagingDcs is not enabled hence DCS request will not be initiated";
    @Inject
    private Logger logger;

    @Inject
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Inject
    private UtcClock clock;

    @Inject
    private ExecutionService executionService;

    @Inject
    private DcsOperationHelper dcsOperationHelper;

    @Inject
    private FeatureControlGuard featureControlGuard;

    @Handles("public.progression.case-defendant-changed")
    public void processCaseDefendantChanged(final JsonEnvelope envelope) {

        if (!featureControlGuard.isFeatureEnabled(FEATURE_STAGING_DCS)) {
            logger.info(INFO_LOG_FOR_FEATURE_STAGING_DCS_DISABLED);
            return;
        }

        logger.info("Processing public.progression.case-defendant-changed event.");

        final JsonObject defendant = envelope.payloadAsJsonObject().getJsonObject(DEFENDANT);
        final UUID defendantId = fromString(defendant.getString(ID));
        final String caseId = defendant.getString(PROSECUTION_CASE_ID);

        if (isCaseLinked(UUID.fromString(caseId), defendantId)) {

            final JsonMetadata metadata = (JsonMetadata) envelope.metadata();
            DcsUpdateJobData dcsUpdateJobData = new DcsUpdateJobData(caseId, defendantId.toString(), metadata.asJsonObject(), UUID.randomUUID().toString());

            final ExecutionInfo executionInfo = new ExecutionInfo(
                    objectToJsonObjectConverter.convert(dcsUpdateJobData),
                    DEFENDANT_UPDATE_TASK,
                    clock.now(),
                    STARTED,
                    Priority.MEDIUM);

            executionService.executeWith(executionInfo);
        }
    }

    @Handles("public.progression.events.case-or-application-ejected")
    public void processCaseEjected(final JsonEnvelope envelope) {

        if (!featureControlGuard.isFeatureEnabled(FEATURE_STAGING_DCS)) {
            logger.info(INFO_LOG_FOR_FEATURE_STAGING_DCS_DISABLED);
            return;
        }
        logger.info("Processing public.progression.events.case-or-application-ejected event.");

        final JsonObject caseEjected = envelope.payloadAsJsonObject();
        final String caseId = caseEjected.getString(PROSECUTION_CASE_ID, EMPTY);

        if (isNotEmpty(caseId) && dcsOperationHelper.isCaseLinked(fromString(caseId))) {
            dcsOperationHelper.unlinkByCaseId(fromString(caseId));
            logger.info("case ejected for caseId {} and unliked", caseId);
        }
    }

    @SuppressWarnings({"java:S125", "java:S2629"})
    @Handles("public.progression.events.court-document-created")
    public void processCourtDocumentAdded(final JsonEnvelope envelope) {

        if (!featureControlGuard.isFeatureEnabled(FEATURE_STAGING_DCS)) {
            logger.info(INFO_LOG_FOR_FEATURE_STAGING_DCS_DISABLED);
            return;
        }

        logger.info("Processing public.progression.events.court-document-created.");
        final JsonObject documentJsonObject = envelope.payloadAsJsonObject().getJsonObject("courtDocument");

        dcsOperationHelper.processAddCourtDocument(documentJsonObject);

    }

    private boolean isCaseLinked(final UUID caseId, final UUID defendantId) {
        final DcsCaseDetailEntity dcsCaseDetailEntity = dcsCaseDetailRepository.findByCaseIdDefendantId(caseId, defendantId);
        return Optional.ofNullable(dcsCaseDetailEntity)
                .map(caseDetailEntity -> caseDetailEntity.getDcsDefendantStatus().equalsIgnoreCase(String.valueOf(LINKED))).orElse(false);
    }
}

package uk.gov.moj.cpp.staging.dcs.event.processor;

import static uk.gov.hmcts.dcs.openapi.model.UpdateDefendantRepresentationRequest.ActionEnum.DELETE;
import static uk.gov.hmcts.dcs.openapi.model.UpdateDefendantRepresentationRequest.ActionEnum.UPDATE;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DEFENDANT_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.FEATURE_STAGING_DCS;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.ORGANISATION_ID;
import static uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus.LINKED;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.DEFENCE_REPRESENTATION_TASK;

import uk.gov.hmcts.dcs.openapi.model.UpdateDefendantRepresentationRequest;
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
import uk.gov.moj.cpp.staging.dcs.event.jobstore.jobdata.DcsDefenceUpdateJobData;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;

import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import org.slf4j.Logger;

@ServiceComponent(EVENT_PROCESSOR)
public class DefenceEventProcessor {
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
    private FeatureControlGuard featureControlGuard;

    @Handles("public.defence.defence-organisation-associated")
    public void processDefenceOrganisationAssociated(final JsonEnvelope envelope) {

        if (!featureControlGuard.isFeatureEnabled(FEATURE_STAGING_DCS)) {
            logger.info("Feature StagingDcs is not enabled hence DCS request will not be initiated");
            return;
        }

        logger.info("Processing public.defence.defence-organisation-associated.");
        processDefenceOrganisationAssociateAndDisassociated(envelope, UPDATE.getValue());
    }

    @Handles("public.defence.defence-organisation-disassociated")
    public void processDefenceOrganisationDisassociated(final JsonEnvelope envelope) {

        if (!featureControlGuard.isFeatureEnabled(FEATURE_STAGING_DCS)) {
            logger.info("Feature StagingDcs is not enabled hence DCS request will not be initiated");
            return;
        }

        logger.info("Processing public.defence.defence-organisation-disassociated.");
        processDefenceOrganisationAssociateAndDisassociated(envelope, DELETE.getValue());
    }

    private void processDefenceOrganisationAssociateAndDisassociated(final JsonEnvelope envelope, final String action) {
        final JsonObject jsonObject = envelope.payloadAsJsonObject();
        final String caseId = jsonObject.getString(CASE_ID);
        final String defendantId = jsonObject.getString(DEFENDANT_ID);
        final String organisationId = jsonObject.getString(ORGANISATION_ID);

        if(isCaseLinked(UUID.fromString(caseId), UUID.fromString(defendantId))) {

            final JsonMetadata metadata = (JsonMetadata) envelope.metadata();
            DcsDefenceUpdateJobData dcsDefenceUpdateJobData = new DcsDefenceUpdateJobData(caseId, defendantId,
                    organisationId, metadata.asJsonObject(), action, UUID.randomUUID().toString());

            final ExecutionInfo executionInfo = new ExecutionInfo(
                    objectToJsonObjectConverter.convert(dcsDefenceUpdateJobData),
                    DEFENCE_REPRESENTATION_TASK,
                    clock.now(),
                    STARTED,
                    Priority.MEDIUM);

            executionService.executeWith(executionInfo);
        }
    }

    private boolean isCaseLinked(final UUID caseId, final UUID defendantId) {
        final DcsCaseDetailEntity dcsCaseDetailEntity = dcsCaseDetailRepository.findByCaseIdDefendantId(caseId, defendantId);
        return Optional.ofNullable(dcsCaseDetailEntity)
                .map(caseDetailEntity -> caseDetailEntity.getDcsDefendantStatus().equalsIgnoreCase(String.valueOf(LINKED))).orElse(false);
    }
}

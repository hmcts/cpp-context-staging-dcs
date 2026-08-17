package uk.gov.moj.cpp.staging.dcs.command.api;

import static uk.gov.justice.services.core.annotation.Component.COMMAND_API;
import static uk.gov.justice.services.core.enveloper.Enveloper.envelop;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DEFENDANTS;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.FEATURE_STAGING_DCS;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.core.annotation.FeatureControl;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.staging.dcs.command.api.service.CreateDcsCaseRequestService;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.DcsCaseCreateRequest;
import uk.gov.moj.cpp.staging.dcs.event.processor.DcsCaseRequestProcessor;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.CaseDefendantOffencesEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.CaseDefendantOffencesRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import org.slf4j.Logger;

@SuppressWarnings({"squid:S3740", "squid:S6813"})
@ServiceComponent(COMMAND_API)
public class DcsCaseCommandApi {
    public static final String TRANSACTION_REF = "transactionRef";
    @Inject
    private Logger logger;
    @Inject
    private DcsCaseRequestProcessor dcsCaseRequestProcessor;

    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Inject
    private CreateDcsCaseRequestService createDcsCaseRequestService;

    @Inject
    private CaseDefendantOffencesRepository caseDefendantOffencesRepository;

    @Inject
    private DcsCaseDetailRepository dcsCaseDetailRepository;
    @Inject
    private DcsOperationHelper dcsOperationHelper;

    @Handles("stagingdcs.submit-dcs-case-record")
    @FeatureControl(FEATURE_STAGING_DCS)
    public Envelope createCaseRequest(final JsonEnvelope envelope) {

        logger.info("Received stagingdcs.submit-dcs-case-record command");
        final JsonObject payload = envelope.payloadAsJsonObject();
        final DcsCaseCreateRequest request = jsonObjectToObjectConverter.convert(payload, DcsCaseCreateRequest.class);
        final UUID caseId = request.getCaseId();

        request.getDefendants().stream()
                .filter(d -> !createDcsCaseRequestService.isCaseUnlinkedOrFailed(caseId))
                .forEach(d -> createDcsCaseRequestService.createDcsCase(request,d));

        request.getDefendants().stream()
                .forEach(defendant -> {
                    final List<CaseDefendantOffencesEntity> caseDefendantOffencesEntities = caseDefendantOffencesRepository.findByCaseIdDefendantId(caseId, defendant.getId());
                    if(caseDefendantOffencesEntities.isEmpty()){
                        dcsOperationHelper.unlinkByCaseId(caseId);

                        List<DcsCaseDetailEntity> dcsCaseDetailEntities = dcsCaseDetailRepository.findByCaseId(caseId);
                        dcsCaseDetailEntities.stream().forEach(dcsCaseDetailEntity1 -> {
                            if(dcsCaseDetailEntity1.getDcsDefendantStatus().equalsIgnoreCase(DcsDefendantStatus.PENDING.toString())){
                                dcsCaseDetailRepository.deleteByCaseIdDefendantId(dcsCaseDetailEntity1.getCaseId(),dcsCaseDetailEntity1.getDefendantId());
                            }
                        });
                    }
                });

        if (!createDcsCaseRequestService.isCaseUnlinkedOrFailed(caseId)) {
            final JsonObject transformedPayload = createDcsCaseRequestService.transformPayload(payload);
            if(!transformedPayload.getJsonArray(DEFENDANTS).isEmpty()) {
                dcsCaseRequestProcessor.processDcsCaseRequestHandler(transformedPayload, envelope.metadata());
            }
        }

        return envelop(payload)
                .withName("stagingdcs.dcs-case-record-submitted")
                .withMetadataFrom(envelope);
    }

    @Handles("stagingdcs.process-dcs-transaction-status")
    public JsonEnvelope processTransactionStatus(final JsonEnvelope envelope) {
        return envelope;
    }
}
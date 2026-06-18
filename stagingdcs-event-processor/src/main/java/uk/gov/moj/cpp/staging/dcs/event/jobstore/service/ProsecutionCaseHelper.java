package uk.gov.moj.cpp.staging.dcs.event.jobstore.service;

import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.CASE_ID;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.ProsecutionCase;
import uk.gov.moj.cpp.staging.dcs.event.service.ProgressionService;

import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.slf4j.Logger;

public class ProsecutionCaseHelper {
    @Inject
    public Logger logger;
    @Inject
    public JsonObjectToObjectConverter jsonObjectToObjectConverter;
    @Inject
    private ProgressionService progressionService;
    public ProsecutionCase getProsecutionCase(final JsonObject metadata, final UUID caseId) {
        try {
            final JsonEnvelope requestEnvelopeWithCaseId = JsonEnvelope.envelopeFrom(
                    metadataFrom(metadata),
                    createObjectBuilder()
                            .add(CASE_ID, caseId.toString())
            );
            final JsonObject prosecutionCaseJson = progressionService.getProsecutionCaseByCaseId(requestEnvelopeWithCaseId, caseId.toString());
            return jsonObjectToObjectConverter.convert(prosecutionCaseJson, ProsecutionCase.class);
        } catch (Exception e){
            logger.info("Exception occurred while getProsecutionCase - {}", e.getMessage());
        }
        return null;
    }
}

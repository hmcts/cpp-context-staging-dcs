package uk.gov.moj.cpp.staging.dcs.command.api;

import static uk.gov.justice.services.core.annotation.Component.COMMAND_API;
import static uk.gov.justice.services.core.enveloper.Enveloper.envelop;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.slf4j.Logger;

@ServiceComponent(COMMAND_API)
public class CreateDcsCaseCommandApi {

    @Inject
    private Logger logger;

    @Handles("stagingdcs.create-case-defendant-file-in-dcs")
    public Envelope createCaseRequest(final JsonEnvelope envelope) {
        logger.info("Received stagingdcs.create-case-defendant-file-in-dcs command");
        final JsonObject payload = envelope.payloadAsJsonObject();



        return envelop(payload)
                .withName("stagingdcs.case-defendant-file-created-in-dcs")
                .withMetadataFrom(envelope);
    }
}
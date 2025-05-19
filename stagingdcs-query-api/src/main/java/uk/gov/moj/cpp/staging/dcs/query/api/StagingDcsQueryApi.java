package uk.gov.moj.cpp.staging.dcs.query.api;

import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.JsonEnvelope;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

@ServiceComponent(Component.QUERY_API)
public class StagingDcsQueryApi {

    @Handles("stagingdcs.query.status-by-case-id")
    public JsonEnvelope fetchStatusBasedOnCaseId(final JsonEnvelope envelope) {
        final JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();

        return envelopeFrom(envelope.metadata(), jsonObjectBuilder.build());
    }
}

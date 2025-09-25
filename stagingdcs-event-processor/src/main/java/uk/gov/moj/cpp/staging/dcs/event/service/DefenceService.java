package uk.gov.moj.cpp.staging.dcs.event.service;

import static javax.json.Json.createObjectBuilder;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DEFENDANT_ID;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import java.util.Optional;

import javax.inject.Inject;
import javax.json.JsonObject;

public class DefenceService {

    private static final String QUERY_GET_ASSOCIATED_ORGANISATION = "defence.query.associated-organisation";

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private Requester requester;

    public Optional<JsonObject> getAssociatedOrganisation(final Metadata metadata, final String defendantId) {
        final Metadata metadataWithActionName = metadataFrom(metadata).withName(QUERY_GET_ASSOCIATED_ORGANISATION).build();
        final JsonObject requestParameter = createObjectBuilder()
                .add(DEFENDANT_ID, defendantId)
                .build();
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, requestParameter);
        final Envelope<JsonObject> response = requester.requestAsAdmin(requestEnvelope, JsonObject.class);
        return Optional.ofNullable(response.payload());
    }
}

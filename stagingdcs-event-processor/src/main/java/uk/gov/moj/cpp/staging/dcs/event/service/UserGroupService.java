package uk.gov.moj.cpp.staging.dcs.event.service;

import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

public class UserGroupService {

    private static final String ORGANISTION_ID = "organisationId";
    private static final String QUERY_GET_ORGANISATION_DETAILS = "usersgroups.get-organisation-details";

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private Requester requester;

    public JsonObject getOrganisationDetails(final Metadata metadata, final String organisationId) {
        final Metadata metadataWithActionName = metadataFrom(metadata).withName(QUERY_GET_ORGANISATION_DETAILS).build();
        final JsonObject requestParameter = createObjectBuilder()
                .add(ORGANISTION_ID, organisationId)
                .build();
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, requestParameter);
        final Envelope<JsonObject> response = requester.requestAsAdmin(requestEnvelope, JsonObject.class);
        return response.payload();
    }
}

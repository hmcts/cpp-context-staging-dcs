package uk.gov.moj.cpp.staging.dcs.event.service;

import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.courts.progression.query.CourtdocumentsAll;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class ProgressionService {

    private static final String CASE_ID = "caseId";
    private static final String PROGRESSION_QUERY_PROSECUTION_CASE = "progression.query.prosecutioncase-v2";
    private static final String PROGRESSION_QUERY_COURT_DOCUMENTS_SEARCH = "progression.query.courtdocuments-all";
    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private Requester requester;

    public JsonObject getProsecutionCaseByCaseId(final JsonEnvelope envelope, final String caseId) {
        final Metadata metadataWithActionName = metadataFrom(envelope.metadata())
                .withName(PROGRESSION_QUERY_PROSECUTION_CASE).build();
        final JsonObject requestParameter = createObjectBuilder()
                .add(CASE_ID, caseId)
                .build();
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, requestParameter);
        final Envelope<JsonObject> response = requester.requestAsAdmin(requestEnvelope, JsonObject.class);
        return response.payload().getJsonObject("prosecutionCase");
    }

    public CourtdocumentsAll getCourtDocumentsByParams(final String caseId, final String defendantId) {
        CourtdocumentsAll courtdocumentsAll = null;
        final Metadata metadataWithActionName = metadataBuilder()
                .withName(PROGRESSION_QUERY_COURT_DOCUMENTS_SEARCH)
                .withId(randomUUID())
                .build();
        final JsonObjectBuilder paramBuilder = createObjectBuilder()
                .add(CASE_ID, caseId);

        if (isNotEmpty(defendantId)) {
            paramBuilder.add("defendantId", defendantId);

        }

        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, paramBuilder.build());
        final Envelope<JsonObject> response = requester.requestAsAdmin(requestEnvelope, JsonObject.class);
        if (nonNull(response.payload())) {
            courtdocumentsAll = jsonObjectToObjectConverter.convert(response.payload(), CourtdocumentsAll.class);
        }
        return courtdocumentsAll;
    }

}

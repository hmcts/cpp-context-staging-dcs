package uk.gov.moj.cpp.staging.dcs.util;

import static uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClientProvider.newPublicJmsMessageConsumerClientProvider;
import static uk.gov.justice.services.integrationtest.utils.jms.JmsMessageProducerClientProvider.newPublicJmsMessageProducerClientProvider;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.staging.dcs.util.FileUtil.getPayload;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClient;
import uk.gov.justice.services.integrationtest.utils.jms.JmsMessageProducerClient;
import uk.gov.justice.services.messaging.JsonEnvelope;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.json.JsonObject;

import org.junit.jupiter.api.Assertions;


public class QueueUtil {

    private static final long RETRIEVE_TIMEOUT = 90000;
    private static final JmsMessageConsumerClient publicEventConsumerForDefendantChanged = newPublicJmsMessageConsumerClientProvider().withEventNames("public.progression.case-defendant-changed").getMessageConsumerClient();
    private static final JmsMessageConsumerClient publicEventConsumerForCourtDocumentAdded = newPublicJmsMessageConsumerClientProvider().withEventNames("public.progression.events.court-document-created").getMessageConsumerClient();
    private static final JmsMessageConsumerClient publicEventConsumerForDefenceOrganisationAssociated = newPublicJmsMessageConsumerClientProvider().withEventNames("public.defence.defence-organisation-associated").getMessageConsumerClient();
    private static final JmsMessageConsumerClient publicEventConsumerForDefenceOrganisationDisassociated = newPublicJmsMessageConsumerClientProvider().withEventNames("public.defence.defence-organisation-disassociated").getMessageConsumerClient();
    private static JmsMessageProducerClient publicMessageProducerClient = newPublicJmsMessageProducerClientProvider()
            .getMessageProducerClient();

    public static Optional<JsonObject> retrieveMessageBody(final JmsMessageConsumerClient consumer) {
        return consumer.retrieveMessageAsJsonEnvelope(RETRIEVE_TIMEOUT).map(JsonEnvelope::payloadAsJsonObject);
    }

    public static Optional<JsonObject> retrieveMessageBody(final JmsMessageConsumerClient consumer, final long retrieveTimeout) {
        return consumer.retrieveMessageAsJsonEnvelope(retrieveTimeout).map(JsonEnvelope::payloadAsJsonObject);
    }

    public static void sendPublicEvent(final String name, final String payloadResource, final String caseId, final String defendantId) {

        String body = getPayload(payloadResource)
                .replaceAll("CASE_ID", caseId)
                .replaceAll("DEFENDANT_ID", defendantId);
        final JsonObject jsonObjectPayload = new StringToJsonObjectConverter().convert(body);
        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder().withId(UUID.randomUUID()).withName(name).build(), jsonObjectPayload);

        publicMessageProducerClient.sendMessage(name, publicEvent);
    }

    public static void sendPublicEvent(final String name, final String payloadResource, final Map<String,String> replaceValues) {

        String body = getPayload(payloadResource);
        for (Map.Entry<String, String> entry : replaceValues.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            body = body.replaceAll(key, value);
        }
        final JsonObject jsonObjectPayload = new StringToJsonObjectConverter().convert(body);
        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder().withId(UUID.randomUUID()).withName(name).build(), jsonObjectPayload);

        publicMessageProducerClient.sendMessage(name, publicEvent);
    }

    public static void sendDefencePublicEvent(final String name, final String payloadResource, final String caseId, final String defendantId, final String organisationId) {

        String body = getPayload(payloadResource)
                .replaceAll("CASE_ID", caseId)
                .replaceAll("DEFENDANT_ID", defendantId)
                .replaceAll("ORGANISATION_ID", organisationId);
        final JsonObject jsonObjectPayload = new StringToJsonObjectConverter().convert(body);
        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder().withId(UUID.randomUUID()).withName(name).build(), jsonObjectPayload);

        publicMessageProducerClient.sendMessage(name, publicEvent);
    }
    public static void verifyEventMessage(JmsMessageConsumerClient eventConsumer) {
        final Optional<JsonObject> message = retrieveMessageBody(eventConsumer);
        Assertions.assertTrue(message.isPresent());
    }

    public static JsonObject verifyAndReturnEventMessage(JmsMessageConsumerClient eventConsumer) {
        final Optional<JsonObject> message = retrieveMessageBody(eventConsumer);
        Assertions.assertTrue(message.isPresent());
        return message.get();
    }

    public static void verifyPublicEventCaseDefendantChanged() {
        verifyEventMessage(publicEventConsumerForDefendantChanged);
    }

    public static JsonObject verifyPublicProgressionCourtDocumentAdded() {
        return verifyAndReturnEventMessage(publicEventConsumerForCourtDocumentAdded);
    }

    public static void verifyPublicEventConsumerForDefenceOrganisationAssociated() {
        verifyEventMessage(publicEventConsumerForDefenceOrganisationAssociated);
    }

    public static void verifyPublicEventConsumerForDefenceOrganisationDisassociated() {
        verifyEventMessage(publicEventConsumerForDefenceOrganisationDisassociated);
    }
}

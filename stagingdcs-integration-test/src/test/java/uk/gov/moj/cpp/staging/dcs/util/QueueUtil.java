package uk.gov.moj.cpp.staging.dcs.util;

import uk.gov.justice.services.integrationtest.utils.jms.JmsMessageConsumerClient;
import uk.gov.justice.services.messaging.JsonEnvelope;

import java.util.Optional;

import javax.json.JsonObject;


public class QueueUtil {

    private static final long RETRIEVE_TIMEOUT = 90000;

    public static Optional<JsonObject> retrieveMessageBody(final JmsMessageConsumerClient consumer) {
        return consumer.retrieveMessageAsJsonEnvelope(RETRIEVE_TIMEOUT).map(JsonEnvelope::payloadAsJsonObject);
    }

    public static Optional<JsonObject> retrieveMessageBody(final JmsMessageConsumerClient consumer, final long retrieveTimeout) {
        return consumer.retrieveMessageAsJsonEnvelope(retrieveTimeout).map(JsonEnvelope::payloadAsJsonObject);
    }
}

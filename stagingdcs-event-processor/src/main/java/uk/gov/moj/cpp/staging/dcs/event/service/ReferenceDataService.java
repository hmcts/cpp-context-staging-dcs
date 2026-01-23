package uk.gov.moj.cpp.staging.dcs.event.service;

import static java.time.format.DateTimeFormatter.ISO_DATE;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.json.JsonObject;

public class ReferenceDataService {

    private static final String ID = "id";
    private static final String PROSECUTOR_CODE = "prosecutorCode";
    private static final String DOCUMENTS_TYPE_ACCESS = "documentsTypeAccess";
    private static final String REFERENCE_DATA_QUERY_GET_PROSECUTORS = "referencedata.query.prosecutors";
    private static final String REFERENCE_DATA_QUERY_GET_ALL_DOCUMENT_TYPE_ACCESS = "referencedata.get-all-document-type-access";
    private static final String REFERENCE_DATA_QUERY_GET_DOCUMENT_TYPE_ACCESS_BY_ID = "referencedata.query.document-type-access";
    public static final String PROSECUTORS = "prosecutors";
    public static final String SEND_TO_DCS = "sendToDcs";

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private Requester requester;


    public Optional<JsonObject> getProsecutorByProsecutionAuthority(final String prosecutionAuthority) {
        final JsonObject payload = createObjectBuilder().add(PROSECUTOR_CODE, prosecutionAuthority).build();
        final Metadata metadata = JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCE_DATA_QUERY_GET_PROSECUTORS)
                .build();

        final Envelope<JsonObject> response = requester.requestAsAdmin(envelopeFrom(metadata, payload), JsonObject.class);
        if (isEmpty(response.payload().getJsonArray(PROSECUTORS))) {
            return Optional.empty();
        }
        return ofNullable(response.payload().getJsonArray(PROSECUTORS).getJsonObject(0));
    }

    public Optional<JsonObject> getDocumentTypeAccessById(final String documentTypeId) {
        final JsonObject payload = createObjectBuilder().add(ID, documentTypeId).build();
        final Metadata metadata = JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCE_DATA_QUERY_GET_DOCUMENT_TYPE_ACCESS_BY_ID)
                .build();

        final Envelope<JsonObject> response = requester.requestAsAdmin(envelopeFrom(metadata, payload), JsonObject.class);
        if (isNull(response.payload())) {
            return Optional.empty();
        }
        return Optional.of(response.payload());
    }

    public Optional<JsonObject> getAllDocumentTypeAccess() {
        final JsonObject payload = createObjectBuilder()
                .add("date", LocalDate.now().format(ISO_DATE))
                .build();
        final Metadata metadata = JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCE_DATA_QUERY_GET_ALL_DOCUMENT_TYPE_ACCESS)
                .build();

        final Envelope<JsonObject> response = requester.requestAsAdmin(envelopeFrom(metadata, payload), JsonObject.class);
        if (isNull(response.payload())) {
            return Optional.empty();
        }
        return Optional.of(response.payload());
    }

    public Map<String,Boolean> createDocumentAccessTypeMap() {
        final Map<String, Boolean> documentTypeMap = new HashMap<>();
        final Optional<JsonObject> responsePayload = getAllDocumentTypeAccess();
        if (responsePayload.isEmpty()) {
            return documentTypeMap;
        }
        responsePayload.ifPresent(resObject -> resObject.getJsonArray(DOCUMENTS_TYPE_ACCESS)
                .getValuesAs(JsonObject.class)
                .forEach(docType -> documentTypeMap.put(docType.getString(ID), docType.getBoolean(SEND_TO_DCS, false))));
        return documentTypeMap;
    }
}

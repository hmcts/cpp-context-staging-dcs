package uk.gov.moj.cpp.staging.dcs.event.service;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.moj.cpp.staging.dcs.event.util.FileUtil.getPayload;
import static uk.gov.moj.cpp.staging.dcs.event.util.FileUtil.jsonFromString;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;

import java.util.Map;
import java.util.Optional;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceTest {
    private static final String ID = "id";
    private static final String DOCUMENTS_TYPE_ACCESS = "documentsTypeAccess";
    private static final String REFERENCE_DATA_QUERY_GET_ALL_DOCUMENT_TYPE_ACCESS = "referencedata.get-all-document-type-access";
    private static final String REFERENCE_DATA_QUERY_GET_DOCUMENT_TYPE_ACCESS_BY_ID = "referencedata.query.document-type-access";

    @InjectMocks
    private ReferenceDataService referenceDataService;

    @Mock
    private Requester requester;

    @Test
    void shouldgGetDocumentTypeAccessById() {
        final String documentTypeId = randomUUID().toString();
        Envelope responseEnvelope = envelopeFrom(metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCE_DATA_QUERY_GET_DOCUMENT_TYPE_ACCESS_BY_ID)
                .build(),
                getReferenceDataDocumentType(documentTypeId).get());
        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenReturn(responseEnvelope);

        final Optional<JsonObject> documentTypeObject = referenceDataService.getDocumentTypeAccessById(documentTypeId);

        assertThat(documentTypeObject, notNullValue());
        JsonObject responsePayload = documentTypeObject.get();
        assertThat(responsePayload.getString(ID), is(documentTypeId));
    }

    @Test
    void shouldGetAllDocumentTypeAccess() {
        final String documentTypeId = randomUUID().toString();
        JsonObject responseJsonObject = createObjectBuilder()
                .add(DOCUMENTS_TYPE_ACCESS, createArrayBuilder()
                        .add(getReferenceDataDocumentType(documentTypeId).get())
                        .build()
                ).build();

        Envelope responseEnvelope = envelopeFrom(metadataBuilder()
                        .withId(randomUUID())
                        .withName(REFERENCE_DATA_QUERY_GET_ALL_DOCUMENT_TYPE_ACCESS)
                        .build(),
                responseJsonObject);
        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenReturn(responseEnvelope);

        final Map<String,Boolean> documentTypeMap = referenceDataService.createDocumentAccessTypeMap();

        assertThat(documentTypeMap, notNullValue());
        assertThat(documentTypeMap.get(documentTypeId), is(true));
    }

    private Optional<JsonObject> getReferenceDataDocumentType(final String documentTypeId){
        final String publicEventPayloadString = getPayload("referencedata.query.document-type-access-by-id.json")
                .replaceAll("DOCUMENT_ACCESS_TYPE_ID", documentTypeId);
        return Optional.of(jsonFromString(publicEventPayloadString));
    }
}
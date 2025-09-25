package uk.gov.moj.cpp.staging.dcs.event.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.moj.cpp.staging.dcs.event.util.TestUtil.createAssociationObject;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import java.util.Optional;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefenceServiceTest {

    public static final String QUERY_GET_ASSOCIATED_ORGANISATION = "defence.query.associated-organisation";
    public static final String DEFENDANT_ID = "defendantId";
    @Mock
    private Requester requester;

    @InjectMocks
    private DefenceService defenceService;

    @Captor
    private ArgumentCaptor<Envelope<JsonObject>> envelopeCaptor;

    @Test
    void shouldGetAssociatedOrganisation() {
        final Metadata metadata = metadataBuilder().withName(QUERY_GET_ASSOCIATED_ORGANISATION).withId(randomUUID()).withUserId(randomUUID().toString()).build();

        final String defendantId = randomUUID().toString();


        final JsonObject associationObject = createAssociationObject();
        final Envelope envelope = JsonEnvelope.envelopeFrom(metadata, associationObject);

        when(requester.requestAsAdmin(envelopeCaptor.capture(), any())).thenReturn(envelope);

        final Optional<JsonObject> actualAssociationObject = defenceService.getAssociatedOrganisation(metadata, defendantId);

        assertThat(actualAssociationObject, is(Optional.of(associationObject)));
        assertThat(envelopeCaptor.getValue().metadata().name(), is(QUERY_GET_ASSOCIATED_ORGANISATION));
        assertThat(envelopeCaptor.getValue().payload().getString(DEFENDANT_ID), is(defendantId));
    }
}
package uk.gov.moj.cpp.staging.dcs.event.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.moj.cpp.staging.dcs.event.util.TestUtil.createOrganisationObject;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.Organisation;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserGroupServiceTest {

    private static final String QUERY_GET_ORGANISATION_DETAILS = "usersgroups.get-organisation-details";
    public static final String ORGANISATION_ID = "organisationId";
    @Mock
    private Requester requester;

    @InjectMocks
    private UserGroupService userGroupService;

    @Captor
    private ArgumentCaptor<Envelope<JsonObject>> envelopeCaptor;

    @Mock
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Test
    void shouldGetOrganisationDetails() {
        final Metadata metadata = metadataBuilder().withName(QUERY_GET_ORGANISATION_DETAILS).withId(randomUUID()).withUserId(randomUUID().toString()).build();

        final String organisationId = randomUUID().toString();

        final JsonObject organisationObject = createOrganisationObject();
        final Envelope envelope = JsonEnvelope.envelopeFrom(metadata, organisationObject);

        when(requester.requestAsAdmin(envelopeCaptor.capture(), any())).thenReturn(envelope);

        final JsonObject organisationDetails = userGroupService.getOrganisationDetails(metadata, organisationId);

        assertThat(organisationDetails, is(organisationObject));
        assertThat(envelopeCaptor.getValue().metadata().name(), is(QUERY_GET_ORGANISATION_DETAILS));
        assertThat(envelopeCaptor.getValue().payload().getString(ORGANISATION_ID), is(organisationId));
    }

}
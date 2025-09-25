package uk.gov.moj.cpp.staging.dcs.event.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.staging.dcs.event.util.TestUtil;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProgressionServiceTest {

    public static final String PROSECUTION_GET_CASE = "progression.query.prosecutioncase-v2";
    public static final String CASE_ID = "caseId";
    public static final String DEFENDANT_ID = "defendantId";
    @Mock
    private Requester requester;

    @Mock
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @InjectMocks
    private ProgressionService progressionService;

    @Captor
    private ArgumentCaptor<Envelope<JsonObject>> envelopeCaptor;

    private final String caseId = randomUUID().toString();


    @Test
    public void shouldGetProsecutionCase() {
        final Metadata metadata = metadataBuilder().withName(PROSECUTION_GET_CASE).withId(randomUUID()).withUserId(randomUUID().toString()).build();

        final String defendantId = randomUUID().toString();
        final JsonObject prosecutionCaseObject = TestUtil.createProsecutionCaseObject(caseId.toString(), defendantId);
        final Envelope envelope = JsonEnvelope.envelopeFrom(metadata, prosecutionCaseObject);


        final JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(metadata, prosecutionCaseObject);


        when(requester.requestAsAdmin(envelopeCaptor.capture(), any())).thenReturn(envelope);

        final JsonObject actualProsecutionCase = progressionService.getProsecutionCaseByCaseId(jsonEnvelope, caseId);

        assertThat(actualProsecutionCase, is(prosecutionCaseObject.getJsonObject("prosecutionCase")));
        assertThat(envelopeCaptor.getValue().metadata().name(), is(PROSECUTION_GET_CASE));
        assertThat(envelopeCaptor.getValue().payload().getString(CASE_ID), is(caseId));
    }

}
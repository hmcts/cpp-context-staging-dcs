package uk.gov.moj.cpp.staging.dcs.command.api;

import static java.util.UUID.randomUUID;
import static javax.json.Json.createArrayBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_API;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerClassMatcher.isHandlerClass;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMethodMatcher.method;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.test.utils.core.enveloper.EnveloperFactory;
import uk.gov.moj.cpp.staging.dcs.command.api.service.CreateDcsCaseRequestService;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.DcsCaseCreateRequest;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.DcsDefendant;
import uk.gov.moj.cpp.staging.dcs.event.processor.DcsCaseRequestProcessor;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.CaseDefendantOffencesRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;

import java.util.List;
import java.util.UUID;

import javax.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class DcsCaseCommandApiTest {

    private static final String CREATE_DCS_CASE_COMMAND_NAME = "stagingdcs.submit-dcs-case-record";
    @InjectMocks
    private DcsCaseCommandApi dcsCaseCommandApi;
    @Mock
    private CreateDcsCaseRequestService createDcsCaseRequestService;
    @Spy
    private final Enveloper enveloper = EnveloperFactory.createEnveloper();

    @Mock
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Mock
    private DcsCaseRequestProcessor dcsCaseRequestProcessor;

    @Mock
    private Logger logger;

    @Mock
    private JsonObject payload;

    private DcsCaseCreateRequest dcsCaseCreateRequest;

    @Mock
    private CaseDefendantOffencesRepository caseDefendantOffencesRepository;

    @Mock
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Mock
    private DcsOperationHelper dcsOperationHelper;

    @BeforeEach
    void setUp() {
        dcsCaseCreateRequest = DcsCaseCreateRequest.Builder
                .newDcsCaseCreateRequest()
                .withCaseId(randomUUID())
                .withDefendants(List.of(DcsDefendant.Builder.newDefendant().withId(randomUUID()).build()))
                .build();
    }

    @Test
    void shouldHandleCreateDcsCaseCommandRequest() {

        assertThat(DcsCaseCommandApi.class, isHandlerClass(COMMAND_API)
                .with(method("createCaseRequest")
                        .thatHandles(CREATE_DCS_CASE_COMMAND_NAME)));
    }

    @Test
    void shouldProcessDcsTransactionStatusCommand() {

        when(jsonObjectToObjectConverter.convert(payload, DcsCaseCreateRequest.class)).thenReturn(dcsCaseCreateRequest);
        when(createDcsCaseRequestService.isCaseUnlinkedOrFailed(any())).thenReturn(false);
        final JsonEnvelope command = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName(CREATE_DCS_CASE_COMMAND_NAME).withUserId(randomUUID().toString()).build(), payload);

        final DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        when(createDcsCaseRequestService.transformPayload(any())).thenReturn(payload);
        when(payload.getJsonArray(any())).thenReturn(createArrayBuilder().build());

        dcsCaseCommandApi.createCaseRequest(command);

        Mockito.verify(createDcsCaseRequestService, times(2)).isCaseUnlinkedOrFailed(any(UUID.class));
        Mockito.verify(createDcsCaseRequestService, times(1)).createDcsCase(any(DcsCaseCreateRequest.class), any());

    }

    @Test
    void shouldProcessCreateDcsCaseCommandAndNotCreateNewRecordForLinkedDefendant() {

        when(jsonObjectToObjectConverter.convert(payload, DcsCaseCreateRequest.class)).thenReturn(dcsCaseCreateRequest);
        when(createDcsCaseRequestService.isCaseUnlinkedOrFailed(any())).thenReturn(true);
        final JsonEnvelope command = envelopeFrom(metadataBuilder().withId(randomUUID())
                .withName(CREATE_DCS_CASE_COMMAND_NAME).withUserId(randomUUID().toString()).build(), payload);

        final DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());

        dcsCaseCommandApi.createCaseRequest(command);

        Mockito.verify(createDcsCaseRequestService, times(2)).isCaseUnlinkedOrFailed(any(UUID.class));
        Mockito.verify(createDcsCaseRequestService, times(0)).createDcsCase(any(DcsCaseCreateRequest.class), any());

    }

}

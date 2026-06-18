package uk.gov.justice.api.resource;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.apache.hc.core5.http.HttpStatus.SC_ACCEPTED;
import static org.apache.hc.core5.http.HttpStatus.SC_BAD_REQUEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.interceptor.InterceptorChainProcessor;
import uk.gov.justice.services.core.interceptor.spi.InterceptorContextProvider;
import uk.gov.justice.services.test.utils.core.enveloper.EnveloperFactory;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;

import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class DefaultCommandApiTransactionTransactionRefResourceTest {
    @InjectMocks
    private DefaultCommandApiTransactionTransactionRefResource defaultCommandApiTransactionTransactionRefResource;

    @Spy
    private final Enveloper enveloper = EnveloperFactory.createEnveloper();

    @Mock
    private Logger logger;

    @Mock
    private DcsOperationHelper dcsOperationHelper;

    @Mock
    private InterceptorChainProcessor interceptorChainProcessor;
    @Mock
    private InterceptorContextProvider interceptorContextProvider;

    @Test
    void shouldProcessDcsTransactionStatusCommand() {
        final UUID tranRefId = randomUUID();
        final UUID userId = randomUUID();
        JsonObject payload = createObjectBuilder()
                .add("transactionRef", randomUUID().toString())
                .add("successPayload", createObjectBuilder()
                        .add("caseId", randomUUID().toString())
                        .add("caseReferral", randomUUID().toString())
                        .add("defendants", createArrayBuilder().add(createObjectBuilder()
                                        .add("defendantId", randomUUID().toString())
                                        .add("defendantReferral", randomUUID().toString())
                                )
                                .build())
                ).build();

        Response response = defaultCommandApiTransactionTransactionRefResource.processTransactionStatus(userId.toString(), tranRefId.toString(), payload);

        verify(dcsOperationHelper, times(1)).updateTransactionStatus(any(), any());
        assertThat(response, notNullValue());
        assertThat(response.getStatus(), is(SC_ACCEPTED));

    }

    @Test
    void shouldNotProcessDcsTransactionStatusCommand_ResponseBadRequest() {
        final UUID tranRefId = randomUUID();
        final UUID userId = randomUUID();
        JsonObject payload = createObjectBuilder()
                .add("unknownProp1", "unknownValue1")
                .build();
        doThrow(new RuntimeException("runtimeException")).when(dcsOperationHelper).updateTransactionStatus(any(),any());

        Response response = defaultCommandApiTransactionTransactionRefResource.processTransactionStatus(userId.toString(), tranRefId.toString(), payload);

        verify(dcsOperationHelper, times(1)).updateTransactionStatus(any(), any());
        assertThat(response, notNullValue());
        assertThat(response.getStatus(), is(SC_BAD_REQUEST));
        assertThat(response.readEntity(String.class), containsString(tranRefId.toString()));

    }
}

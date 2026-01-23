package uk.gov.moj.cpp.staging.dcs.event.processor;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.DCS_NOTIFICATION_TASK;

import uk.gov.hmcts.dcs.openapi.model.LinkCaseAndDefendantRequest;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.event.service.LinkCaseAndDefendantRequestConverter;

import javax.json.JsonObject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class DcsCaseRequestProcessorTest {
    @InjectMocks
    private DcsCaseRequestProcessor DcsCaseRequestProcessor;
    @Mock
    private ExecutionService executionService;
    @Captor
    private ArgumentCaptor<ExecutionInfo> executionInfoCaptor;
    @Mock
    private Logger logger;
    @Spy
    private final JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(new ObjectMapperProducer().objectMapper());
    @Spy
    private static ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter();
    @Mock
    private UtcClock clock;
    @Mock
    private LinkCaseAndDefendantRequestConverter linkCaseAndDefendantRequestConverter;

    @BeforeAll
    public static void createObjectToJsonObjectConverter() {
        setField(objectToJsonObjectConverter, "mapper", new ObjectMapperProducer().objectMapper());
    }

    @Test
    void shouldProcessDcsCaseRequestHandler() {
        final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest = mock(LinkCaseAndDefendantRequest.class);
        final String caseId = randomUUID().toString();
        final JsonObject payload = createObjectBuilder()
                .add("caseId", caseId)
                .add("caseUrn", "TEST123")
                .add("defendants", createArrayBuilder().build())
                .build();

        final Metadata metadata = metadataBuilder().withName("stagingdcs.submit-dcs-case-record").withId(randomUUID()).build();
        DcsCaseRequestProcessor.processDcsCaseRequestHandler(payload, metadata);
        verify(executionService).executeWith(executionInfoCaptor.capture());
        final ExecutionInfo dcsNotificationExecutionInfo = executionInfoCaptor.getValue();

        assertThat(dcsNotificationExecutionInfo.getNextTask(), is(DCS_NOTIFICATION_TASK));
        verify(logger, times(1)).info("Added DCS_NOTIFICATION_TASK to jobstore for caseId {}", caseId);
    }
}
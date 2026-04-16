package uk.gov.moj.cpp.staging.dcs.event.processor;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.DEFENCE_REPRESENTATION_TASK;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;

import java.util.UUID;

import uk.gov.justice.services.messaging.JsonObjects;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class DefenceEventProcessorTest {

    @InjectMocks
    private DefenceEventProcessor defenceEventProcessor;
    @Mock
    private ExecutionService executionService;
    @Captor
    private ArgumentCaptor<ExecutionInfo> executionInfoCaptor;
    @Mock
    private Logger logger;
    @Spy
    private static ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter();
    @Mock
    private UtcClock clock;
    @Mock
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Mock
    private FeatureControlGuard featureControlGuard;

    @BeforeAll
    public static void createObjectToJsonObjectConverter() {
        setField(objectToJsonObjectConverter, "mapper", new ObjectMapperProducer().objectMapper());
    }

    @BeforeEach
    void setUp(){
        when(featureControlGuard.isFeatureEnabled("StagingDcs")).thenReturn(true);
    }

    @Test
    void shouldProcessDcsCaseRequestHandlerForLinkedCaseDefenceAssociated() {
        final UUID defendantId = randomUUID();
        final UUID caseId = randomUUID();

        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder()
                .withId(UUID.randomUUID())
                .withName("public.defence.defence-organisation-associated")
                .build(), createDefenceAssociatedObject(caseId, defendantId));

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.toString());
        when(dcsCaseDetailRepository.findByCaseIdDefendantId(caseId, defendantId)).thenReturn(dcsCaseDetailEntity);
        defenceEventProcessor.processDefenceOrganisationAssociated(publicEvent);

        verify(executionService).executeWith(executionInfoCaptor.capture());
        final ExecutionInfo dcsNotificationExecutionInfo = executionInfoCaptor.getValue();

        assertThat(dcsNotificationExecutionInfo.getNextTask(), is(DEFENCE_REPRESENTATION_TASK));
    }

    @Test
    void shouldProcessDcsCaseRequestHandlerForLinkedCaseDefenceDisassociated() {
        final UUID defendantId = randomUUID();
        final UUID caseId = randomUUID();

        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder()
                .withId(UUID.randomUUID())
                .withName("public.defence.defence-organisation-disassociated")
                .build(), createDefenceDisassociatedObject(caseId, defendantId));

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.toString());
        when(dcsCaseDetailRepository.findByCaseIdDefendantId(caseId, defendantId)).thenReturn(dcsCaseDetailEntity);
        defenceEventProcessor.processDefenceOrganisationDisassociated(publicEvent);

        verify(executionService).executeWith(executionInfoCaptor.capture());
        final ExecutionInfo dcsNotificationExecutionInfo = executionInfoCaptor.getValue();

        assertThat(dcsNotificationExecutionInfo.getNextTask(), is(DEFENCE_REPRESENTATION_TASK));
    }

    @ParameterizedTest
    @EnumSource(value = DcsDefendantStatus.class, names = {"UNLINKED", "FAILED"})
    void shouldNotProcessDcsCaseRequestHandlerForUnLinkedAndFailedCaseAssociated(DcsDefendantStatus dcsDefendantStatus) {
        final UUID defendantId = randomUUID();
        final UUID caseId = randomUUID();

        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder()
                .withId(UUID.randomUUID())
                .withName("public.defence.defence-organisation-associated")
                .build(), createDefenceAssociatedObject(caseId, defendantId));

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setDcsDefendantStatus(dcsDefendantStatus.toString());
        when(dcsCaseDetailRepository.findByCaseIdDefendantId(caseId, defendantId)).thenReturn(dcsCaseDetailEntity);
        defenceEventProcessor.processDefenceOrganisationAssociated(publicEvent);

        verify(executionService, times(0)).executeWith(executionInfoCaptor.capture());

    }

    @ParameterizedTest
    @EnumSource(value = DcsDefendantStatus.class, names = {"UNLINKED", "FAILED"})
    void shouldNotProcessDcsCaseRequestHandlerForUnLinkedAndFailedCaseDisassociated(DcsDefendantStatus dcsDefendantStatus) {
        final UUID defendantId = randomUUID();
        final UUID caseId = randomUUID();

        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder()
                .withId(UUID.randomUUID())
                .withName("public.defence.defence-organisation-disassociated")
                .build(), createDefenceAssociatedObject(caseId, defendantId));

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setDcsDefendantStatus(dcsDefendantStatus.toString());
        when(dcsCaseDetailRepository.findByCaseIdDefendantId(caseId, defendantId)).thenReturn(dcsCaseDetailEntity);
        defenceEventProcessor.processDefenceOrganisationDisassociated(publicEvent);

        verify(executionService, times(0)).executeWith(executionInfoCaptor.capture());

    }

    private JsonObject createDefenceAssociatedObject(final UUID caseId, final UUID defendantId) {
        JsonObjectBuilder actionOne = createJsonObjectBuilder(randomUUID().toString(), "DefendantDocuments");
        JsonObjectBuilder actionTwo = createJsonObjectBuilder(randomUUID().toString(), "DefenceClient");

        JsonArrayBuilder permissionsArray = JsonObjects.createArrayBuilder()
                .add(actionOne)
                .add(actionTwo);

        JsonObjectBuilder associatedObject = JsonObjects.createObjectBuilder()
                .add("caseId", String.valueOf(caseId))
                .add("defendantId", String.valueOf(defendantId))
                .add("laaContractNumber", "1234567")
                .add("organisationId", randomUUID().toString())
                .add("organisationName", "William & Co LLP")
                .add("permissions", permissionsArray)
                .add("representationType", "REPRESENTATION_ORDER")
                .add("startDate", "2025-06-25T12:49:35.458Z")
                .add("userId", randomUUID().toString());
        return associatedObject.build();
    }

    private JsonObject createDefenceDisassociatedObject(final UUID caseId, final UUID defendantId) {
        JsonObjectBuilder actionOne = createJsonObjectBuilder(randomUUID().toString(), "DefendantDocuments");
        JsonObjectBuilder actionTwo = createJsonObjectBuilder(randomUUID().toString(), "DefenceClient");

        JsonArrayBuilder permissionsArray = JsonObjects.createArrayBuilder()
                .add(actionOne)
                .add(actionTwo);

        JsonObjectBuilder associatedObject = JsonObjects.createObjectBuilder()
                .add("caseId", String.valueOf(caseId))
                .add("defendantId", String.valueOf(defendantId))
                .add("organisationId", randomUUID().toString())
                .add("permissions", permissionsArray)
                .add("userId", randomUUID().toString());
        return associatedObject.build();
    }

    private JsonObjectBuilder createJsonObjectBuilder(final String id, final String object) {
        return JsonObjects.createObjectBuilder()
                .add("action", "View")
                .add("id", id)
                .add("object", object)
                .add("source", randomUUID().toString())
                .add("status", "Added")
                .add("target", randomUUID().toString());
    }

}
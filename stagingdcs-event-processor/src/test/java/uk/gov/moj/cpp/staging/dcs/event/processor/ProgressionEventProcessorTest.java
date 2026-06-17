package uk.gov.moj.cpp.staging.dcs.event.processor;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.DEFENDANT_UPDATE_TASK;
import static uk.gov.moj.cpp.staging.dcs.event.util.FileUtil.getPayload;
import static uk.gov.moj.cpp.staging.dcs.event.util.FileUtil.jsonFromString;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.MaterialService;
import uk.gov.moj.cpp.staging.dcs.event.service.ReferenceDataService;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;

import java.util.UUID;

import javax.json.JsonObject;

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
class ProgressionEventProcessorTest {

    @InjectMocks
    private ProgressionEventProcessor progressionEventProcessor;
    @Mock
    private ExecutionService executionService;
    @Captor
    private ArgumentCaptor<ExecutionInfo> executionInfoCaptor;
    @Mock
    private Logger logger;
    @Spy
    private static ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(new ObjectMapperProducer().objectMapper());

    @Spy
    private  static JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(new ObjectMapperProducer().objectMapper());
    @Mock
    private UtcClock clock;
    @Mock
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Mock
    private FeatureControlGuard featureControlGuard;

    @Mock
    private DcsOperationHelper dcsOperationHelper;

    @Mock
    ReferenceDataService referenceDataService;

    @Mock
    MaterialService materialService;

    @BeforeAll
    public static void createObjectToJsonObjectConverter() {
        setField(objectToJsonObjectConverter, "mapper", new ObjectMapperProducer().objectMapper());
    }

    @BeforeEach
    void setUp(){
        when(featureControlGuard.isFeatureEnabled("StagingDcs")).thenReturn(true);
    }

    @Test
    void shouldProcessDcsCaseRequestHandlerForLinkedCase() {
        final UUID defendantId = randomUUID();
        final UUID caseId = randomUUID();

        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder()
                                        .withId(UUID.randomUUID())
                                        .withName("public.progression.case-defendant-changed")
                                        .build(), createDefendantChangedPayload(caseId, defendantId));

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.toString());
        when(dcsCaseDetailRepository.findByCaseIdDefendantId(caseId, defendantId)).thenReturn(dcsCaseDetailEntity);
        progressionEventProcessor.processCaseDefendantChanged(publicEvent);

        verify(executionService).executeWith(executionInfoCaptor.capture());
        final ExecutionInfo dcsNotificationExecutionInfo = executionInfoCaptor.getValue();

        assertThat(dcsNotificationExecutionInfo.getNextTask(), is(DEFENDANT_UPDATE_TASK));
    }

    @ParameterizedTest
    @EnumSource(value = DcsDefendantStatus.class, names = {"UNLINKED", "FAILED"})
    void shouldNotProcessDcsCaseRequestHandlerForUnLinkedAndFailedCase(DcsDefendantStatus dcsDefendantStatus) {
        final UUID defendantId = randomUUID();
        final UUID caseId = randomUUID();

        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder()
                                        .withId(UUID.randomUUID())
                                        .withName("public.progression.case-defendant-changed")
                                        .build(), createDefendantChangedPayload(caseId, defendantId));

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setDcsDefendantStatus(dcsDefendantStatus.toString());
        when(dcsCaseDetailRepository.findByCaseIdDefendantId(caseId, defendantId)).thenReturn(dcsCaseDetailEntity);
        progressionEventProcessor.processCaseDefendantChanged(publicEvent);

        verify(executionService, times(0)).executeWith(executionInfoCaptor.capture());

    }

    @Test
    void shouldNotProcessDefendantUpdateEvent_WhenFeatureIsFalse() {
        when(featureControlGuard.isFeatureEnabled("StagingDcs")).thenReturn(false);
        final UUID defendantId = randomUUID();
        final UUID caseId = randomUUID();

        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder()
                .withId(UUID.randomUUID())
                .withName("public.progression.case-defendant-changed")
                .build(), createDefendantChangedPayload(caseId, defendantId));

        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setDcsDefendantStatus(DcsDefendantStatus.LINKED.toString());
        progressionEventProcessor.processCaseDefendantChanged(publicEvent);

        verify(executionService, times(0)).executeWith(any());
    }

    @Test
    void shouldNotProcessCaseEjectedEvent_WhenFeatureIsFalse() {
        when(featureControlGuard.isFeatureEnabled("StagingDcs")).thenReturn(false);
        final UUID caseId = randomUUID();

        final JsonObject publicEventPayload = createObjectBuilder()
                .add("prosecutionCaseId", caseId.toString())
                .add("removalReason", "case removed from system")
                .build();

        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder()
                .withId(UUID.randomUUID())
                .withName("public.progression.events.case-or-application-ejected")
                .build(), publicEventPayload);
        progressionEventProcessor.processCaseEjected(publicEvent);

        verify(dcsOperationHelper, times(0)).isCaseLinked(any());
        verify(dcsOperationHelper, times(0)).unlinkByCaseId(any());
    }

    @Test
    void shouldProcessCaseEjectedEvent_WhenFeatureIsTrue() {
        when(featureControlGuard.isFeatureEnabled("StagingDcs")).thenReturn(true);
        when(dcsOperationHelper.isCaseLinked(any())).thenReturn(true);
        final UUID caseId = randomUUID();

        final JsonObject publicEventPayload = createObjectBuilder()
                .add("prosecutionCaseId", caseId.toString())
                .add("removalReason", "case removed from system")
                .build();

        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder()
                .withId(UUID.randomUUID())
                .withName("public.progression.events.case-or-application-ejected")
                .build(), publicEventPayload);
        progressionEventProcessor.processCaseEjected(publicEvent);

        verify(dcsOperationHelper, times(1)).isCaseLinked(any());
        verify(dcsOperationHelper, times(1)).unlinkByCaseId(any());
    }

    @Test
    void shouldProcessCourtDocumentAdded_WhenFeatureIsTrue_CallProcessCourtDocumentTask() {
        when(featureControlGuard.isFeatureEnabled("StagingDcs")).thenReturn(true);
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String publicEventPayloadString = getPayload("public.progression.events.court-document-created-defendant-level.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());

        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder()
                .withId(UUID.randomUUID())
                .withName("public.progression.events.court-document-created")
                .build(), jsonFromString(publicEventPayloadString));
        progressionEventProcessor.processCourtDocumentAdded(publicEvent);

        final ArgumentCaptor<JsonObject> courtDocumentObject = ArgumentCaptor.forClass(JsonObject.class);
        verify(dcsOperationHelper, times(1)).processAddCourtDocument(courtDocumentObject.capture());
        final JsonObject processAddCourtDocumentTaskData = courtDocumentObject.getValue();
        final JsonObject pubicEventJsonObject = new StringToJsonObjectConverter().convert(publicEventPayloadString);
        assertThat(pubicEventJsonObject.getJsonObject("courtDocument"), is(processAddCourtDocumentTaskData));
    }

    @Test
    void shouldNotProcessCourtDocumentAdded_WhenFeatureIsFalse() {
        when(featureControlGuard.isFeatureEnabled("StagingDcs")).thenReturn(false);
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID materialId = randomUUID();
        final UUID documentTypeId = randomUUID();

        final String publicEventPayloadString = getPayload("public.progression.events.court-document-created-case-level.json")
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("DOCUMENT_TYPE_ID", documentTypeId.toString())
                .replaceAll("MATERIAL_ID", materialId.toString());


        final JsonEnvelope publicEvent = envelopeFrom(metadataBuilder()
                .withId(UUID.randomUUID())
                .withName("public.progression.events.court-document-created")
                .build(), jsonFromString(publicEventPayloadString));
        progressionEventProcessor.processCourtDocumentAdded(publicEvent);
        verify(dcsOperationHelper, times(0)).processAddCourtDocument(any());
    }

    private JsonObject createDefendantChangedPayload(final UUID caseId, final UUID defendantId) {
        return createObjectBuilder()
                .add("defendant", createObjectBuilder()
                        .add("id", String.valueOf(defendantId))
                        .add("prosecutionCaseId", String.valueOf(caseId))
                        .add("personDefendant", createObjectBuilder()
                                .add("personDetails", createObjectBuilder()
                                        .add("title", "Mr")
                                        .add("firstName", "Mark")
                                        .add("lastName", "Taylor")
                                        .add("dateOfBirth", "1998-10-28")
                                        .build())
                                .build())

                        .build())
                .build();
    }
}
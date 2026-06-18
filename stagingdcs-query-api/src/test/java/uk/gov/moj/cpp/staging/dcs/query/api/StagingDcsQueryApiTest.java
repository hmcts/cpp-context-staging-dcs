package uk.gov.moj.cpp.staging.dcs.query.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.SUCCESS;
import static uk.gov.moj.cpp.staging.dcs.query.api.util.DateUtil.ZONE_DATETIME_FORMATTER;

import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionMetadataEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import uk.gov.justice.services.messaging.JsonObjects;
import javax.json.JsonObject;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StagingDcsQueryApiTest {

    private static final String LINKED_TO_DCS = "LINKED TO DCS";
    private static final String NOT_LINKED_TO_DCS = "NOT LINKED TO DCS";
    private static final String DCS_LINK_SEVERED = "DCS LINK SEVERED";
    private static final String DCS_UPDATED = "DCS UPDATED";
    private static final String DCS_NOT_UPDATED = "DCS NOT UPDATED";
    private static final String CASE_ID_FIELD = "caseId";
    private static final String DEFENDANT_ID_FIELD = "defendantId";
    private static final String DEFENDANT_STATUS_FIELD = "defendantStatus";
    private static final String CASE_STATUS_FIELD = "caseStatus";
    private static final String DEFENDANTS_FIELD = "defendants";
    private static final String LATEST_DEFENDANT_OPERATIONS_FIELD = "latestDefendantOperations";
    private static final String TRANSACTION_ID_FIELD = "transactionId";
    private static final String STATUS_FIELD = "status";
    private static final String TYPE_FIELD = "type";
    private static final String UPDATED_TIME_FIELD = "updatedTime";

    @Mock
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Mock
    private TransactionMetadataRepository transactionMetadataRepository;

    @InjectMocks
    private StagingDcsQueryApi stagingDcsQueryApi;

    @ParameterizedTest
    @MethodSource("provideStatusesForSingleCaseAndSingleDefendant")
    void shouldFetchCaseDetailByCaseIdForSingleCaseAndSingleDefendant(String defendantStatus, String defendantExpectedStatus, String caseExpectedStatus,String transactionStatus, String expectedTransactionStatus) {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID transactionId1 = randomUUID();
        final UUID transactionId2 = randomUUID();
        final UUID transactionId3 = randomUUID();
        final ZonedDateTime updatedAt1 = ZonedDateTime.now();
        final ZonedDateTime updatedAt2 = ZonedDateTime.now().plusMinutes(1);
        final ZonedDateTime updatedAt3 = ZonedDateTime.now().plusMinutes(1);

        when(dcsCaseDetailRepository.findByCaseId(caseId)).thenReturn(List.of(createDcsCaseDetailEntity(caseId, defendantId, defendantStatus)));
        when(transactionMetadataRepository.findByCaseIdAndDefendantId(caseId, defendantId))
                .thenReturn(List.of(createTransactionMetadataEntity(caseId, defendantId, transactionStatus, TransactionType.DEFENDANT_UPDATE, transactionId1, updatedAt1),
                        createTransactionMetadataEntity(caseId, defendantId, transactionStatus, TransactionType.DEFENCE_REPRESENTATION, transactionId3, updatedAt3),
                        createTransactionMetadataEntity(caseId, defendantId, transactionStatus, TransactionType.DEFENCE_REPRESENTATION, transactionId2, updatedAt2)));

        JsonObject jsonObject = JsonObjects.createObjectBuilder()
                .add("caseId", caseId.toString())
                .build();
        JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName("stagingdcs.query.dcscase-status-by-case-id")
                        .build(),
                jsonObject);
        JsonEnvelope response = stagingDcsQueryApi.fetchCaseDetailByCaseId(jsonEnvelope);
        JsonObject responsePayload = response.asJsonObject();

        assertEquals(caseId.toString(), responsePayload.getString(CASE_ID_FIELD));
        assertEquals(caseExpectedStatus, responsePayload.getString(CASE_STATUS_FIELD));

        assertEquals(1, responsePayload.getJsonArray(DEFENDANTS_FIELD).size());
        final JsonObject defendant = responsePayload.getJsonArray(DEFENDANTS_FIELD).getJsonObject(0);
        assertEquals(defendantId.toString(), defendant.getString(DEFENDANT_ID_FIELD));
        assertEquals(defendantExpectedStatus, defendant.getString(DEFENDANT_STATUS_FIELD));

        assertEquals(2, defendant.getJsonArray(LATEST_DEFENDANT_OPERATIONS_FIELD).size());

        final JsonObject latestOperation1 = defendant.getJsonArray(LATEST_DEFENDANT_OPERATIONS_FIELD).getJsonObject(0);
        assertEquals(TransactionType.DEFENDANT_UPDATE.name(), latestOperation1.getString(TYPE_FIELD));
        assertEquals(transactionId1.toString(), latestOperation1.getString(TRANSACTION_ID_FIELD));
        assertEquals(expectedTransactionStatus, latestOperation1.getString(STATUS_FIELD));
        assertEquals(updatedAt1.format(ZONE_DATETIME_FORMATTER).toString(), latestOperation1.getString(UPDATED_TIME_FIELD));

        final JsonObject latestOperation2 = defendant.getJsonArray(LATEST_DEFENDANT_OPERATIONS_FIELD).getJsonObject(1);
        assertEquals(TransactionType.DEFENCE_REPRESENTATION.name(), latestOperation2.getString(TYPE_FIELD));
        assertEquals(transactionId3.toString(), latestOperation2.getString(TRANSACTION_ID_FIELD));
        assertEquals(expectedTransactionStatus, latestOperation2.getString(STATUS_FIELD));
        assertEquals(updatedAt3.format(ZONE_DATETIME_FORMATTER).toString(), latestOperation2.getString(UPDATED_TIME_FIELD));
    }

    @ParameterizedTest
    @MethodSource("provideStatusesForSingleCaseAndMultipleDefendants")
    void shouldFetchCaseDetailByCaseIdForSingleCaseAndMultipleDefendants(String defendant1Status, String expectedDefendant1Status,
                                                                         String defendant1Transaction1Status, String expectedDefendant1Transaction1Status,
                                                                         String defendant1Transaction2Status, String expectedDefendant1Transaction2Status,
                                                                         String defendant2Status, String expectedDefendant2Status,
                                                                         String defendant2Transaction1Status, String expectedDefendant2Transaction1Status,
                                                                         String defendant2Transaction2Status, String expectedDefendant2Transaction2Status,
                                                                         String expectedCaseStatus) {
        final UUID caseId = randomUUID();
        final UUID defendantId1 = randomUUID();
        final UUID defendantId2 = randomUUID();
        final UUID transactionId1_1 = randomUUID();
        final UUID transactionId1_2 = randomUUID();
        final UUID transactionId2_1 = randomUUID();
        final UUID transactionId2_2 = randomUUID();
        final ZonedDateTime updatedAt1_1 = ZonedDateTime.now();
        final ZonedDateTime updatedAt1_2 = ZonedDateTime.now().plusMinutes(1);
        final ZonedDateTime updatedAt2_1 = ZonedDateTime.now().plusMinutes(2);
        final ZonedDateTime updatedAt2_2 = ZonedDateTime.now().plusMinutes(3);

        when(dcsCaseDetailRepository.findByCaseId(caseId))
                .thenReturn(List.of(createDcsCaseDetailEntity(caseId, defendantId1, defendant1Status),
                        createDcsCaseDetailEntity(caseId, defendantId2, defendant2Status)));
        when(transactionMetadataRepository.findByCaseIdAndDefendantId(caseId, defendantId1))
                .thenReturn(List.of(createTransactionMetadataEntity(caseId, defendantId1, defendant1Transaction1Status, TransactionType.DEFENDANT_UPDATE, transactionId1_1, updatedAt1_1),
                        createTransactionMetadataEntity(caseId, defendantId1, defendant1Transaction2Status, TransactionType.DEFENCE_REPRESENTATION, transactionId1_2, updatedAt1_2)));
        when(transactionMetadataRepository.findByCaseIdAndDefendantId(caseId, defendantId2))
                .thenReturn(List.of(createTransactionMetadataEntity(caseId, defendantId2, defendant2Transaction1Status, TransactionType.DEFENDANT_UPDATE, transactionId2_1, updatedAt2_1),
                        createTransactionMetadataEntity(caseId, defendantId1, defendant2Transaction2Status, TransactionType.DEFENCE_REPRESENTATION, transactionId2_2, updatedAt2_2)));

        JsonObject jsonObject = JsonObjects.createObjectBuilder()
                .add("caseId", caseId.toString())
                .build();
        JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName("stagingdcs.query.dcscase-status-by-case-id")
                        .build(),
                jsonObject);
        JsonEnvelope response = stagingDcsQueryApi.fetchCaseDetailByCaseId(jsonEnvelope);
        JsonObject responsePayload = response.asJsonObject();

        assertEquals(caseId.toString(), responsePayload.getString(CASE_ID_FIELD));
        assertEquals(expectedCaseStatus, responsePayload.getString(CASE_STATUS_FIELD));

        assertEquals(2, responsePayload.getJsonArray(DEFENDANTS_FIELD).size());
        final JsonObject defendant1 = responsePayload.getJsonArray(DEFENDANTS_FIELD).getJsonObject(0);
        assertEquals(defendantId1.toString(), defendant1.getString(DEFENDANT_ID_FIELD));
        assertEquals(expectedDefendant1Status, defendant1.getString(DEFENDANT_STATUS_FIELD));

        assertEquals(2, defendant1.getJsonArray(LATEST_DEFENDANT_OPERATIONS_FIELD).size());

        final JsonObject latestOperation1_1 = defendant1.getJsonArray(LATEST_DEFENDANT_OPERATIONS_FIELD).getJsonObject(0);
        assertEquals(TransactionType.DEFENDANT_UPDATE.name(), latestOperation1_1.getString(TYPE_FIELD));
        assertEquals(transactionId1_1.toString(), latestOperation1_1.getString(TRANSACTION_ID_FIELD));
        assertEquals(expectedDefendant1Transaction1Status, latestOperation1_1.getString(STATUS_FIELD));
        assertEquals(updatedAt1_1.format(ZONE_DATETIME_FORMATTER).toString(), latestOperation1_1.getString(UPDATED_TIME_FIELD));

        final JsonObject latestOperation1_2 = defendant1.getJsonArray(LATEST_DEFENDANT_OPERATIONS_FIELD).getJsonObject(1);
        assertEquals(TransactionType.DEFENCE_REPRESENTATION.name(), latestOperation1_2.getString(TYPE_FIELD));
        assertEquals(transactionId1_2.toString(), latestOperation1_2.getString(TRANSACTION_ID_FIELD));
        assertEquals(expectedDefendant1Transaction2Status, latestOperation1_2.getString(STATUS_FIELD));
        assertEquals(updatedAt1_2.format(ZONE_DATETIME_FORMATTER).toString(), latestOperation1_2.getString(UPDATED_TIME_FIELD));

        final JsonObject defendant2 = responsePayload.getJsonArray(DEFENDANTS_FIELD).getJsonObject(1);
        assertEquals(defendantId2.toString(), defendant2.getString(DEFENDANT_ID_FIELD));
        assertEquals(expectedDefendant2Status, defendant2.getString(DEFENDANT_STATUS_FIELD));

        assertEquals(2, defendant2.getJsonArray(LATEST_DEFENDANT_OPERATIONS_FIELD).size());

        final JsonObject latestOperation2_1 = defendant2.getJsonArray(LATEST_DEFENDANT_OPERATIONS_FIELD).getJsonObject(0);
        assertEquals(TransactionType.DEFENDANT_UPDATE.name(), latestOperation2_1.getString(TYPE_FIELD));
        assertEquals(transactionId2_1.toString(), latestOperation2_1.getString(TRANSACTION_ID_FIELD));
        assertEquals(expectedDefendant2Transaction1Status, latestOperation2_1.getString(STATUS_FIELD));
        assertEquals(updatedAt2_1.format(ZONE_DATETIME_FORMATTER).toString(), latestOperation2_1.getString(UPDATED_TIME_FIELD));

        final JsonObject latestOperation2_2 = defendant2.getJsonArray(LATEST_DEFENDANT_OPERATIONS_FIELD).getJsonObject(1);
        assertEquals(TransactionType.DEFENCE_REPRESENTATION.name(), latestOperation2_2.getString(TYPE_FIELD));
        assertEquals(transactionId2_2.toString(), latestOperation2_2.getString(TRANSACTION_ID_FIELD));
        assertEquals(expectedDefendant2Transaction2Status, latestOperation2_2.getString(STATUS_FIELD));
        assertEquals(updatedAt2_2.format(ZONE_DATETIME_FORMATTER).toString(), latestOperation2_2.getString(UPDATED_TIME_FIELD));
    }

    @ParameterizedTest
    @MethodSource("provideStatusesForSingleCaseAndSingleDefendant")
    void shouldFetchCaseDetailByCaseIdDefendantIdAndGetResult(String defendantStatus, String defendantExpectedStatus, String caseExpectedStatus, String transactionStatus, String expectedTransactionStatus) {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID transactionId1 = randomUUID();
        final UUID transactionId2 = randomUUID();
        final UUID transactionId3 = randomUUID();
        final ZonedDateTime updatedAt1 = ZonedDateTime.now();
        final ZonedDateTime updatedAt2 = ZonedDateTime.now().plusMinutes(1);
        final ZonedDateTime updatedAt3 = ZonedDateTime.now().plusMinutes(1);

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(caseId, defendantId)).thenReturn(createDcsCaseDetailEntity(caseId, defendantId, defendantStatus));
        when(transactionMetadataRepository.findByCaseIdAndDefendantId(caseId, defendantId))
                .thenReturn(List.of(createTransactionMetadataEntity(caseId, defendantId, transactionStatus, TransactionType.DEFENDANT_UPDATE, transactionId1, updatedAt1),
                        createTransactionMetadataEntity(caseId, defendantId, transactionStatus, TransactionType.DEFENCE_REPRESENTATION, transactionId3, updatedAt3),
                        createTransactionMetadataEntity(caseId, defendantId, transactionStatus, TransactionType.DEFENCE_REPRESENTATION, transactionId2, updatedAt2)));

        JsonObject jsonObject = JsonObjects.createObjectBuilder()
                .add("caseId", caseId.toString())
                .add("defendantId", defendantId.toString())
                .build();
        JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName("stagingdcs.query.dcscase-status-by-case-id-defendant-id")
                        .build(),
                jsonObject);
        JsonEnvelope response = stagingDcsQueryApi.fetchCaseDetailByCaseIdDefendantId(jsonEnvelope);
        JsonObject responsePayload = response.asJsonObject();

        assertEquals(caseId.toString(), responsePayload.getString(CASE_ID_FIELD));
        assertEquals(caseExpectedStatus, responsePayload.getString(CASE_STATUS_FIELD));

        assertEquals(1, responsePayload.getJsonArray(DEFENDANTS_FIELD).size());
        final JsonObject defendant = responsePayload.getJsonArray(DEFENDANTS_FIELD).getJsonObject(0);
        assertEquals(defendantId.toString(), defendant.getString(DEFENDANT_ID_FIELD));
        assertEquals(defendantExpectedStatus, defendant.getString(DEFENDANT_STATUS_FIELD));

        assertEquals(2, defendant.getJsonArray(LATEST_DEFENDANT_OPERATIONS_FIELD).size());

        final JsonObject latestOperation1 = defendant.getJsonArray(LATEST_DEFENDANT_OPERATIONS_FIELD).getJsonObject(0);
        assertEquals(TransactionType.DEFENDANT_UPDATE.name(), latestOperation1.getString(TYPE_FIELD));
        assertEquals(transactionId1.toString(), latestOperation1.getString(TRANSACTION_ID_FIELD));
        assertEquals(expectedTransactionStatus, latestOperation1.getString(STATUS_FIELD));
        assertEquals(updatedAt1.format(ZONE_DATETIME_FORMATTER).toString(), latestOperation1.getString(UPDATED_TIME_FIELD));

        final JsonObject latestOperation2 = defendant.getJsonArray(LATEST_DEFENDANT_OPERATIONS_FIELD).getJsonObject(1);
        assertEquals(TransactionType.DEFENCE_REPRESENTATION.name(), latestOperation2.getString(TYPE_FIELD));
        assertEquals(transactionId3.toString(), latestOperation2.getString(TRANSACTION_ID_FIELD));
        assertEquals(expectedTransactionStatus, latestOperation2.getString(STATUS_FIELD));
        assertEquals(updatedAt3.format(ZONE_DATETIME_FORMATTER).toString(), latestOperation2.getString(UPDATED_TIME_FIELD));
    }

    @ParameterizedTest
    @MethodSource("provideNonTerminalStatusesForSingleCaseAndSingleDefendant")
    void shouldNotFetchCaseDetailsWhenStatusAreNotTerminal(String defendantStatus) {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();

        when(dcsCaseDetailRepository.findByCaseIdDefendantId(caseId, defendantId)).thenReturn(createDcsCaseDetailEntity(caseId, defendantId, defendantStatus));

        JsonObject jsonObject = JsonObjects.createObjectBuilder()
                .add("caseId", caseId.toString())
                .add("defendantId", defendantId.toString())
                .build();
        JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName("stagingdcs.query.dcscase-status-by-case-id-defendant-id")
                        .build(),
                jsonObject);
        JsonEnvelope response = stagingDcsQueryApi.fetchCaseDetailByCaseIdDefendantId(jsonEnvelope);
        JsonObject responsePayload = response.asJsonObject();

        assertEquals(caseId.toString(), responsePayload.getString(CASE_ID_FIELD));
        assertThat(responsePayload.containsKey(DEFENDANTS_FIELD), is(false));
        assertThat(responsePayload.containsKey(CASE_STATUS_FIELD), is(false));
    }

    private static Stream<Arguments> provideStatusesForSingleCaseAndSingleDefendant() {
        return Stream.of(
                Arguments.of(DcsDefendantStatus.LINKED.toString(), LINKED_TO_DCS, LINKED_TO_DCS, SUCCESS.toString(), DCS_UPDATED),
                Arguments.of(DcsDefendantStatus.FAILED.toString(), NOT_LINKED_TO_DCS, NOT_LINKED_TO_DCS, TransactionStatus.FAILED.toString(), DCS_NOT_UPDATED),
                Arguments.of(DcsDefendantStatus.UNLINKED.toString(), DCS_LINK_SEVERED, NOT_LINKED_TO_DCS, TransactionStatus.FAILED.toString(), DCS_NOT_UPDATED)
        );
    }

    private static Stream<Arguments> provideNonTerminalStatusesForSingleCaseAndSingleDefendant() {
        return Stream.of(
                Arguments.of(DcsDefendantStatus.PENDING.toString()),
                Arguments.of(DcsDefendantStatus.AWAITING.toString())
        );
    }

    private static Stream<Arguments> provideStatusesForSingleCaseAndMultipleDefendants() {
        return Stream.of(
                Arguments.of(DcsDefendantStatus.LINKED.toString(), LINKED_TO_DCS,
                        SUCCESS.toString(), DCS_UPDATED,
                        SUCCESS.toString(), DCS_UPDATED,
                        DcsDefendantStatus.LINKED.toString(), LINKED_TO_DCS,
                        SUCCESS.toString(), DCS_UPDATED,
                        SUCCESS.toString(), DCS_UPDATED,
                        LINKED_TO_DCS),
                Arguments.of(DcsDefendantStatus.UNLINKED.toString(), DCS_LINK_SEVERED,
                        SUCCESS.toString(), DCS_UPDATED,
                        SUCCESS.toString(), DCS_UPDATED,
                        DcsDefendantStatus.LINKED.toString(), LINKED_TO_DCS,
                        SUCCESS.toString(), DCS_UPDATED,
                        SUCCESS.toString(), DCS_UPDATED,
                        LINKED_TO_DCS),
                Arguments.of(DcsDefendantStatus.LINKED.toString(), LINKED_TO_DCS,
                        SUCCESS.toString(), DCS_UPDATED,
                        SUCCESS.toString(), DCS_UPDATED,
                        DcsDefendantStatus.UNLINKED.toString(), DCS_LINK_SEVERED,
                        SUCCESS.toString(), DCS_UPDATED,
                        SUCCESS.toString(), DCS_UPDATED,
                        LINKED_TO_DCS),
                Arguments.of(DcsDefendantStatus.UNLINKED.toString(), DCS_LINK_SEVERED,
                        SUCCESS.toString(), DCS_UPDATED,
                        SUCCESS.toString(), DCS_UPDATED,
                        DcsDefendantStatus.FAILED.toString(), NOT_LINKED_TO_DCS,
                        SUCCESS.toString(), DCS_UPDATED,
                        TransactionStatus.FAILED.toString(), DCS_NOT_UPDATED,
                        NOT_LINKED_TO_DCS)
        );
    }

    private DcsCaseDetailEntity createDcsCaseDetailEntity(UUID caseId, UUID defendantId, String status) {
        DcsCaseDetailEntity entity = new DcsCaseDetailEntity();
        entity.setCaseId(caseId);
        entity.setDefendantId(defendantId);
        entity.setDcsDefendantStatus(status);
        return entity;
    }

    private TransactionMetadataEntity createTransactionMetadataEntity(UUID caseId, UUID defendantId, String status, TransactionType transactionType, UUID transactionId, ZonedDateTime updatedAt) {
        TransactionMetadataEntity entity = new TransactionMetadataEntity();
        entity.setId(randomUUID());
        entity.setTransactionRefId(transactionId);
        entity.setCaseId(caseId);
        entity.setDefendantId(defendantId);
        entity.setTransactionStatus(status);
        entity.setTransactionType(transactionType.name());
        entity.setCreatedAt(updatedAt);
        entity.setUpdatedAt(updatedAt);
        return entity;
    }
}

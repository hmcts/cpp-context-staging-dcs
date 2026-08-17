package uk.gov.moj.cpp.staging.dcs.query.api;

import static java.time.LocalDate.now;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.staging.dcs.query.api.util.DateUtil.SIMPLE_DATE_FORMAT;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionMetadataEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import uk.gov.justice.services.messaging.JsonObjects;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StagingDcsTransactionQueryApiTest {

    public static final String LIMIT = "limit";
    public static final String SUCCESS = "SUCCESS";
    private static final String CASE_ID_FIELD = "caseId";
    private static final String CASE_OPERATIONS = "caseOperations";
    private static final String DEFENDANTS_FIELD = "defendants";
    public static final String DEFENDANT_OPERATIONS = "defendantOperations";
    public static final String TOTAL_COUNT = "totalCount";
    public static final String MATERIAL_ID = "materialId";
    public static final String TRANSACTION_STATUS = "transactionStatus";
    public static final String TRANSACTION_TYPE = "transactionType";
    public static final String DEFENDANT_ID = "defendantId";
    public static final String PAGE_COUNT = "pageCount";
    public static final String FROM_DATE = "fromDate";
    public static final String TO_DATE = "toDate";
    public static final String TRANSACTION_IDS = "transactionIds";
    public static final String PAYLOAD = "payload";
    public static final String ERROR = "error";
    public static final String STAGINGDCS_QUERY_TRANSACTION_METADATA_FOR_CASE = "stagingdcs.query.transaction-metadata-for-case";
    public static final String STAGINGDCS_QUERY_TRANSACTION_DETAIL = "stagingdcs.query.transaction-detail";
    public static final String TRANSACTIONS_DETAILS = "transactionsDetails";

    @Mock
    private TransactionMetadataRepository transactionMetadataRepository;

    @Mock
    private TransactionDetailRepository transactionDetailRepository;

    @Spy
    private StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();

    @InjectMocks
    private StagingDcsTransactionQueryApi stagingDcsTransactionQueryApi;

    @Test
    void shouldFetchTransactionMetadata() {

        final UUID caseId = randomUUID();
        final List<TransactionMetadataEntity> tranList = getAllTypesOfTransactionList(5, caseId);
        when(transactionMetadataRepository.getTransactionMetadataByCriteria(any())).thenReturn(tranList);
        when(transactionMetadataRepository.getTransactionMetadataCountByCriteria(any())).thenReturn(50L);

        JsonObject jsonObject = JsonObjects.createObjectBuilder()
                .add(CASE_ID_FIELD, caseId.toString())
                .add(DEFENDANT_ID, randomUUID().toString())
                .add(MATERIAL_ID, randomUUID().toString())
                .add(TRANSACTION_TYPE, TransactionType.LINK_DEFENDANT.name())
                .add(TRANSACTION_STATUS, TransactionStatus.SUCCESS.name())
                .add(LIMIT, 10)
                .add(PAGE_COUNT, 0)
                .add(FROM_DATE, now().minusDays(7).format(SIMPLE_DATE_FORMAT).toString())
                .add(TO_DATE, now().format(SIMPLE_DATE_FORMAT).toString())
                .build();
        JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName(STAGINGDCS_QUERY_TRANSACTION_METADATA_FOR_CASE)
                        .build(),
                jsonObject);
        JsonEnvelope response = stagingDcsTransactionQueryApi.fetchCaseTransactionMetadataByCaseId(jsonEnvelope);
        JsonObject responsePayload = response.asJsonObject();
        assertThat(responsePayload, notNullValue());
        assertThat(responsePayload.getString(CASE_ID_FIELD), is(caseId.toString()));
        assertThat(responsePayload.getInt(TOTAL_COUNT), is(50));
        assertThat(responsePayload.getJsonArray(DEFENDANTS_FIELD), hasSize(1));
        assertThat(responsePayload.getJsonArray(DEFENDANTS_FIELD).get(0).asJsonObject().getJsonArray(DEFENDANT_OPERATIONS), hasSize(5));
        assertThat(responsePayload.getJsonArray(CASE_OPERATIONS), hasSize(5));

    }

    @Test
    void shouldNotFetchAnyTransactionMetadata_WhenRepositoryReturnNull() {

        final UUID caseId = randomUUID();
        when(transactionMetadataRepository.getTransactionMetadataByCriteria(any())).thenReturn(null);
        when(transactionMetadataRepository.getTransactionMetadataCountByCriteria(any())).thenReturn(null);

        JsonObject jsonObject = JsonObjects.createObjectBuilder()
                .add(CASE_ID_FIELD, caseId.toString())
                .add(TRANSACTION_TYPE, TransactionType.LINK_DEFENDANT.name())
                .add(TRANSACTION_STATUS, TransactionStatus.SUCCESS.name())
                .add(LIMIT, 10)
                .add(PAGE_COUNT, 0)
                .add(FROM_DATE, now().minusDays(7).format(SIMPLE_DATE_FORMAT).toString())
                .add(TO_DATE, now().format(SIMPLE_DATE_FORMAT).toString())
                .build();
        JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName(STAGINGDCS_QUERY_TRANSACTION_METADATA_FOR_CASE)
                        .build(),
                jsonObject);
        JsonEnvelope response = stagingDcsTransactionQueryApi.fetchCaseTransactionMetadataByCaseId(jsonEnvelope);
        JsonObject responsePayload = response.asJsonObject();
        assertThat(responsePayload, notNullValue());
        assertThat(responsePayload.getString(CASE_ID_FIELD), is(caseId.toString()));
        assertThat(responsePayload.getInt(TOTAL_COUNT), is(0));
        assertThat(responsePayload.containsKey(DEFENDANTS_FIELD), is(false));
        assertThat(responsePayload.containsKey(CASE_OPERATIONS), is(false));

    }

    @Test
    void shouldNotFetchAnyTransactionMetadata_WhenRepositoryReturnEmptyList() {

        final UUID caseId = randomUUID();
        when(transactionMetadataRepository.getTransactionMetadataByCriteria(any())).thenReturn(new ArrayList<>());
        when(transactionMetadataRepository.getTransactionMetadataCountByCriteria(any())).thenReturn(0L);

        JsonObject jsonObject = JsonObjects.createObjectBuilder()
                .add(CASE_ID_FIELD, caseId.toString())
                .add(LIMIT, 10)
                .add(PAGE_COUNT, 0)
                .add(FROM_DATE, now().minusDays(7).format(SIMPLE_DATE_FORMAT).toString())
                .add(TO_DATE, now().format(SIMPLE_DATE_FORMAT).toString())
                .build();
        JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName(STAGINGDCS_QUERY_TRANSACTION_METADATA_FOR_CASE)
                        .build(),
                jsonObject);
        JsonEnvelope response = stagingDcsTransactionQueryApi.fetchCaseTransactionMetadataByCaseId(jsonEnvelope);
        JsonObject responsePayload = response.asJsonObject();
        assertThat(responsePayload, notNullValue());
        assertThat(responsePayload.getString(CASE_ID_FIELD), is(caseId.toString()));
        assertThat(responsePayload.getInt(TOTAL_COUNT), is(0));
        assertThat(responsePayload.containsKey(DEFENDANTS_FIELD), is(false));
        assertThat(responsePayload.containsKey(CASE_OPERATIONS), is(false));

    }

    @Test
    void shouldThrowBadRequestException_WhenDateFormatIsWrong() {

        final UUID caseId = randomUUID();
        JsonObject jsonObject = JsonObjects.createObjectBuilder()
                .add(CASE_ID_FIELD, caseId.toString())
                .add(LIMIT, 10)
                .add(PAGE_COUNT, 0)
                .add(FROM_DATE, "parseException")
                .add(TO_DATE, now().format(SIMPLE_DATE_FORMAT).toString())
                .build();
        JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName(STAGINGDCS_QUERY_TRANSACTION_METADATA_FOR_CASE)
                        .build(),
                jsonObject);
        assertThrows(uk.gov.justice.services.adapter.rest.exception.BadRequestException.class, () -> stagingDcsTransactionQueryApi.fetchCaseTransactionMetadataByCaseId(jsonEnvelope));
    }

    @Test
    void shouldThrowBadRequestException_WhenTransactionTypeValuesAreWrong() {

        final UUID caseId = randomUUID();
        JsonObject jsonObject = JsonObjects.createObjectBuilder()
                .add(CASE_ID_FIELD, caseId.toString())
                .add(TRANSACTION_TYPE, "wrongValue")
                .add(TRANSACTION_STATUS, TransactionStatus.SUCCESS.name())
                .add(LIMIT, 10)
                .add(PAGE_COUNT, 0)
                .add(FROM_DATE, now().minusDays(7).format(SIMPLE_DATE_FORMAT).toString())
                .add(TO_DATE, now().format(SIMPLE_DATE_FORMAT).toString())
                .build();
        JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName(STAGINGDCS_QUERY_TRANSACTION_METADATA_FOR_CASE)
                        .build(),
                jsonObject);
        assertThrows(uk.gov.justice.services.adapter.rest.exception.BadRequestException.class, () -> stagingDcsTransactionQueryApi.fetchCaseTransactionMetadataByCaseId(jsonEnvelope));
    }

    @Test
    void shouldThrowBadRequestException_WhenTransactionStatusValuesAreWrong() {

        final UUID caseId = randomUUID();
        JsonObject jsonObject = JsonObjects.createObjectBuilder()
                .add(CASE_ID_FIELD, caseId.toString())
                .add(TRANSACTION_STATUS, "failed")
                .add(LIMIT, 10)
                .add(PAGE_COUNT, 0)
                .add(FROM_DATE, now().minusDays(7).format(SIMPLE_DATE_FORMAT).toString())
                .add(TO_DATE, now().format(SIMPLE_DATE_FORMAT).toString())
                .build();
        JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName(STAGINGDCS_QUERY_TRANSACTION_METADATA_FOR_CASE)
                        .build(),
                jsonObject);
        assertThrows(uk.gov.justice.services.adapter.rest.exception.BadRequestException.class, () -> stagingDcsTransactionQueryApi.fetchCaseTransactionMetadataByCaseId(jsonEnvelope));
    }

    @Test
    void shouldThrowBadRequestException_WhenTransactionIdsAreWrong() {

        final UUID tranId1 = randomUUID();
        JsonObject jsonObject = JsonObjects.createObjectBuilder()
                .add(TRANSACTION_IDS, tranId1.toString().concat(",").concat("nonUuidString"))
                .build();
        JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName(STAGINGDCS_QUERY_TRANSACTION_DETAIL)
                        .build(),
                jsonObject);
        assertThrows(uk.gov.justice.services.adapter.rest.exception.BadRequestException.class, () -> stagingDcsTransactionQueryApi.fetchTransactionDetails(jsonEnvelope));
    }

    @Test
    void shouldFetchTransactionDetails() {

        final List<TransactionDetailEntity> tranList = getTransactionDetailList(5);
        when(transactionDetailRepository.findByTransactionsByIdList(any())).thenReturn(tranList);

        JsonObject jsonObject = JsonObjects.createObjectBuilder()
                .add(TRANSACTION_IDS, randomUUID().toString().concat(",").concat(randomUUID().toString()))
                .build();
        JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName(STAGINGDCS_QUERY_TRANSACTION_DETAIL)
                        .build(),
                jsonObject);
        JsonEnvelope response = stagingDcsTransactionQueryApi.fetchTransactionDetails(jsonEnvelope);
        JsonObject responsePayload = response.payloadAsJsonObject();
        assertThat(responsePayload, notNullValue());
        final JsonArray tranArray = responsePayload.getJsonArray(TRANSACTIONS_DETAILS);
        assertThat(tranArray, hasSize(5));
        tranArray.getValuesAs(JsonObject.class).forEach(this::verifyEachTransactionDetailJsonObject);
    }

    private void verifyEachTransactionDetailJsonObject(final JsonObject tranObject) {
        assertThat(tranObject.getString(TRANSACTION_STATUS), is(SUCCESS));
        assertThat(tranObject.getString(TRANSACTION_TYPE), is(TransactionType.LINK_DEFENDANT.name()));
        assertThat(tranObject.containsKey(PAYLOAD), is(true));
        assertThat(tranObject.getString(ERROR), containsString("ERROR"));
    }



    private List<TransactionMetadataEntity> getAllTypesOfTransactionList(final int numberOfEntities, final UUID caseId) {
        List<TransactionMetadataEntity> list = new ArrayList<>();
            final UUID defendantId = randomUUID();
            final UUID materialId = randomUUID();
            list.addAll(getTransactionList(numberOfEntities, caseId, defendantId, materialId));
            list.addAll(getTransactionList(numberOfEntities, caseId, null,materialId));

        return list;
    }

    private List<TransactionMetadataEntity> getTransactionList(final int numberOfEntities, final UUID caseId, final UUID defendantId, final UUID materialId) {
        List<TransactionMetadataEntity> list = new ArrayList<>();
        for (int i = 0; i < numberOfEntities; i++) {
            final UUID transactionId = randomUUID();
            list.add(createTransactionMetadataEntity(caseId, defendantId, materialId, SUCCESS, TransactionType.LINK_DEFENDANT,
                    transactionId, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().minusDays(2)));
        }
        return list;
    }

    private List<TransactionDetailEntity> getTransactionDetailList(final int numberOfEntities) {
        List<TransactionDetailEntity> list = new ArrayList<>();
        for (int i = 0; i < numberOfEntities; i++) {
            final UUID transactionId = randomUUID();
            final JsonObject payload = JsonObjects.createObjectBuilder().add("key".concat(String.valueOf(i)), "value".concat(String.valueOf(i))).build();
            final String error = "ERROR".concat(String.valueOf(i));
            list.add(createTransactionDetailEntity(transactionId, SUCCESS, TransactionType.LINK_DEFENDANT,
                    ZonedDateTime.now().minusDays(1), ZonedDateTime.now().minusDays(2), payload.toString(), error));
        }
        return list;
    }

    private TransactionMetadataEntity createTransactionMetadataEntity(UUID caseId, UUID defendantId, UUID materialId, String status, TransactionType transactionType, UUID transactionId, ZonedDateTime updatedAt, ZonedDateTime createdAt) {
        TransactionMetadataEntity entity = new TransactionMetadataEntity();
        entity.setId(randomUUID());
        entity.setTransactionRefId(transactionId);
        entity.setCaseId(caseId);
        entity.setMaterialId(materialId);
        entity.setDefendantId(defendantId);
        entity.setTransactionStatus(status);
        entity.setTransactionType(transactionType.name());
        entity.setUpdatedAt(updatedAt);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private TransactionDetailEntity createTransactionDetailEntity(UUID transactionRefId, String status, TransactionType transactionType, ZonedDateTime updatedAt, ZonedDateTime createdAt, String payload, String error) {
        TransactionDetailEntity entity = new TransactionDetailEntity();
        entity.setTransactionRefId(transactionRefId);
        entity.setTransactionStatus(status);
        entity.setTransactionType(transactionType.name());
        entity.setUpdatedAt(updatedAt);
        entity.setCreatedAt(createdAt);
        entity.setError(error);
        entity.setPayload(payload);
        return entity;
    }
}

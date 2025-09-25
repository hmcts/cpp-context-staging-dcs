package uk.gov.moj.cpp.staging.dcs.query.api;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static javax.json.Json.createArrayBuilder;
import static javax.json.Json.createObjectBuilder;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.staging.dcs.query.api.util.DateUtil.ZONE_DATETIME_FORMATTER;
import static uk.gov.moj.cpp.staging.dcs.query.api.util.DateUtil.getLocalDateFromSimpleDateFormat;

import uk.gov.justice.services.adapter.rest.exception.BadRequestException;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionMetadataEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.pojos.SearchCriteria;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

@ServiceComponent(Component.QUERY_API)
public class StagingDcsTransactionQueryApi {

    private static final String CASE_ID = "caseId";
    private static final String DEFENDANT_ID = "defendantId";
    private static final String MATERIAL_ID = "materialId";
    private static final String TRANSACTION_TYPE = "transactionType";
    private static final String TRANSACTION_STATUS = "transactionStatus";
    private static final String LIMIT = "limit";
    private static final String PAGE_COUNT = "pageCount";
    private static final String FROM_DATE = "fromDate";
    private static final String TO_DATE = "toDate";
    private static final String DEFENDANTS = "defendants";
    private static final String TRANSACTION_ID = "transactionId";
    private static final String UPDATED_TIME = "updatedTime";
    private static final int DEFAULT_LIMIT = 10;
    private static final int DEFAULT_PAGE_COUNT = 0;
    public static final String CASE_OPERATIONS = "caseOperations";
    public static final String DEFENDANT_OPERATIONS = "defendantOperations";
    public static final String TOTAL_COUNT = "totalCount";
    public static final String QUERY_PARAM_TRANSACTION_IDS = "transactionIds";
    public static final String STRING_COMMA = ",";
    public static final String PAYLOAD = "payload";
    public static final String ERROR = "error";
    public static final String CREATED_TIME = "createdTime";
    public static final String TRANSACTIONS_DETAILS = "transactionsDetails";

    @Inject
    private TransactionMetadataRepository transactionMetadataRepository;

    @Inject
    private TransactionDetailRepository transactionDetailRepository;

    @Inject
    private StringToJsonObjectConverter stringToJsonObjectConverter;

    @Handles("stagingdcs.query.transaction-metadata-for-case")
    public JsonEnvelope fetchCaseTransactionMetadataByCaseId(final JsonEnvelope envelope) {
        final JsonObject payloadJsonObject = envelope.payloadAsJsonObject();
        SearchCriteria criteria;
        try {
            criteria = buildSearchCriteria(payloadJsonObject);
        } catch (Exception e) {
            throw new BadRequestException("Failed to parse the query parameters");
        }

        final List<TransactionMetadataEntity> transactionMetadataEntityList = transactionMetadataRepository.getTransactionMetadataByCriteria(criteria);
        final Long totalCountWithoutLimit = transactionMetadataRepository.getTransactionMetadataCountByCriteria(criteria);


        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder();
        jsonObjectBuilder.add(CASE_ID, criteria.getCaseId().toString());
        jsonObjectBuilder.add(TOTAL_COUNT, nonNull(totalCountWithoutLimit) ? totalCountWithoutLimit : 0);
        if (isNotEmpty(transactionMetadataEntityList)) {
            mapTransactionMetadataToJsonResponse(transactionMetadataEntityList, jsonObjectBuilder);
        }
        return envelopeFrom(envelope.metadata(), jsonObjectBuilder.build());
    }

    @Handles("stagingdcs.query.transaction-detail")
    public JsonEnvelope fetchTransactionDetails(final JsonEnvelope envelope) {
        final JsonObject payloadJsonObject = envelope.payloadAsJsonObject();
        final String transactionIds = payloadJsonObject.getString(QUERY_PARAM_TRANSACTION_IDS);
        final List<UUID> transactionIdList;
        try {
            transactionIdList = getVerifiedUuidListFromQueryParam(transactionIds);
        } catch (Exception e) {
            throw new BadRequestException("Failed to parse the query parameters");
        }

        final List<TransactionDetailEntity> transactionDetailEntityList = transactionDetailRepository.findByTransactionsByIdList(transactionIdList);

        final JsonArrayBuilder jsonArrayBuilder = createArrayBuilder();
        transactionDetailEntityList.forEach(dbEntity -> jsonArrayBuilder.add(mapTransactionDetailsToJsonObject(dbEntity)));
        return envelopeFrom(envelope.metadata(), createObjectBuilder()
                .add(TRANSACTIONS_DETAILS, jsonArrayBuilder.build())
                .build()
        );
    }

    private List<UUID> getVerifiedUuidListFromQueryParam(final String queryParam) {
        final String[] uuidArray = queryParam.split(STRING_COMMA);
        return Arrays.stream(uuidArray)
                .map(String::trim)
                .map(UUID::fromString)
                .toList();
    }

    private SearchCriteria buildSearchCriteria(final JsonObject queryPayload) {
        final SearchCriteria searchCriteria = new SearchCriteria();

        searchCriteria.setCaseId(UUID.fromString(queryPayload.getString(CASE_ID)));

        if (queryPayload.containsKey(DEFENDANT_ID)) {
            searchCriteria.setDefendantId(UUID.fromString(queryPayload.getString(DEFENDANT_ID)));
        }

        if (queryPayload.containsKey(MATERIAL_ID)) {
            searchCriteria.setMaterialId(UUID.fromString(queryPayload.getString(MATERIAL_ID)));
        }

        if (queryPayload.containsKey(TRANSACTION_TYPE)) {
            searchCriteria.setTransactionType(TransactionType.valueOf(queryPayload.getString(TRANSACTION_TYPE)).name());
        }

        if (queryPayload.containsKey(TRANSACTION_STATUS)) {
            searchCriteria.setTransactionStatus(TransactionStatus.valueOf(queryPayload.getString(TRANSACTION_STATUS)).name());
        }

        setDateCriteria(queryPayload, searchCriteria);
        setPageAndTransactionLimit(queryPayload, searchCriteria);
        return searchCriteria;
    }

    private void setDateCriteria(final JsonObject queryPayload, final SearchCriteria searchCriteria) {
        if (queryPayload.containsKey(FROM_DATE)) {
            searchCriteria.setFromDate(getLocalDateFromSimpleDateFormat(queryPayload.getString(FROM_DATE)));
        }
        if (queryPayload.containsKey(TO_DATE)) {
            searchCriteria.setToDate(getLocalDateFromSimpleDateFormat(queryPayload.getString(TO_DATE)));
        }
    }

    private void setPageAndTransactionLimit(final JsonObject queryPayload, final SearchCriteria searchCriteria) {
        if (queryPayload.containsKey(LIMIT)) {
            final int limit = queryPayload.getInt(LIMIT);
            searchCriteria.setLimit(limit);
            if (queryPayload.containsKey(PAGE_COUNT)) {
                final int pageCount = queryPayload.getInt(PAGE_COUNT);
                searchCriteria.setOffset(pageCount * limit);
            } else {
                searchCriteria.setOffset(DEFAULT_PAGE_COUNT);
            }
        } else {
            searchCriteria.setLimit(DEFAULT_LIMIT);
            searchCriteria.setOffset(DEFAULT_PAGE_COUNT);
        }

    }

    private JsonObject mapTransactionDetailsToJsonObject(final TransactionDetailEntity entity) {
        final JsonObjectBuilder objectBuilder = createObjectBuilder()
                .add(TRANSACTION_ID, entity.getTransactionRefId().toString())
                .add(TRANSACTION_STATUS, entity.getTransactionStatus())
                .add(TRANSACTION_TYPE, entity.getTransactionType());

        if (isNotEmpty(entity.getPayload())) {
            objectBuilder.add(PAYLOAD, stringToJsonObjectConverter.convert(entity.getPayload()));
        }

        if (isNotEmpty(entity.getError())) {
            objectBuilder.add(ERROR, entity.getError());
        }

        if (nonNull(entity.getUpdatedAt())) {
            objectBuilder.add(UPDATED_TIME, entity.getUpdatedAt().format(ZONE_DATETIME_FORMATTER).toString());
        }

        objectBuilder.add(CREATED_TIME, entity.getCreatedAt().format(ZONE_DATETIME_FORMATTER).toString());

        return objectBuilder.build();

    }

    private JsonObjectBuilder mapTransactionMetadataToJsonResponse(List<TransactionMetadataEntity> transactionMetadataEntityList, final JsonObjectBuilder jsonObjectBuilder) {

        JsonArrayBuilder defendantsArrayBuilder = createArrayBuilder();
        JsonArrayBuilder defendantsOperationsArrayBuilder = createArrayBuilder();
        JsonArrayBuilder caseOperationsArrayBuilder = createArrayBuilder();
        String defendantId = EMPTY;
        for (TransactionMetadataEntity tran : transactionMetadataEntityList) {

            if (isNull(tran.getDefendantId())) {
                caseOperationsArrayBuilder.add(getTransactionJsonObject(tran));
                continue;
            }

            if (EMPTY.equalsIgnoreCase(defendantId) && nonNull(tran.getDefendantId())) {
                defendantsOperationsArrayBuilder.add(getTransactionJsonObject(tran));
                defendantId = tran.getDefendantId().toString();
                continue;
            }

            if (!defendantId.equalsIgnoreCase(tran.getDefendantId().toString())) {
                defendantsArrayBuilder.add(getDefendantJsonObject(defendantId, defendantsOperationsArrayBuilder));
                defendantsOperationsArrayBuilder = createArrayBuilder();
                defendantsOperationsArrayBuilder.add(getTransactionJsonObject(tran));
                defendantId = tran.getDefendantId().toString();
                continue;
            }
            defendantsOperationsArrayBuilder.add(getTransactionJsonObject(tran));
        }

        if (!EMPTY.equalsIgnoreCase(defendantId)) {
            defendantsArrayBuilder.add(getDefendantJsonObject(defendantId, defendantsOperationsArrayBuilder));
        }

        jsonObjectBuilder.add(DEFENDANTS, defendantsArrayBuilder);
        jsonObjectBuilder.add(CASE_OPERATIONS, caseOperationsArrayBuilder);
        return jsonObjectBuilder;
    }

    private JsonObject getTransactionJsonObject(final TransactionMetadataEntity tran) {
        JsonObjectBuilder objectBuilder = Json.createObjectBuilder()
                .add(TRANSACTION_ID, tran.getTransactionRefId().toString())
                .add(TRANSACTION_TYPE, tran.getTransactionType())
                .add(UPDATED_TIME, tran.getUpdatedAt().format(ZONE_DATETIME_FORMATTER).toString());

        if (nonNull(tran.getTransactionStatus())){
            objectBuilder.add(TRANSACTION_STATUS, tran.getTransactionStatus());
        }

        if (nonNull(tran.getMaterialId())) {
            objectBuilder.add(MATERIAL_ID, tran.getMaterialId().toString());
        }

        return objectBuilder.build();

    }

    private JsonObject getDefendantJsonObject(final String defendantId, final JsonArrayBuilder defendantsOperationsArrayBuilder) {
        return createObjectBuilder()
                .add(DEFENDANT_ID, defendantId)
                .add(DEFENDANT_OPERATIONS, defendantsOperationsArrayBuilder.build())
                .build();
    }


}

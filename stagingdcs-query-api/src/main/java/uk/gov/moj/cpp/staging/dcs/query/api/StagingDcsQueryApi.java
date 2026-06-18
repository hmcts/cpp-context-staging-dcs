package uk.gov.moj.cpp.staging.dcs.query.api;

import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.staging.dcs.query.api.mapper.StatusMapper.mapCaseStatus;
import static uk.gov.moj.cpp.staging.dcs.query.api.mapper.StatusMapper.mapDefendantStatus;
import static uk.gov.moj.cpp.staging.dcs.query.api.mapper.StatusMapper.mapTransactionStatus;
import static uk.gov.moj.cpp.staging.dcs.query.api.util.DateUtil.ZONE_DATETIME_FORMATTER;

import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionMetadataEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;
import uk.gov.justice.services.messaging.JsonObjects;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

import org.apache.commons.collections.CollectionUtils;

@ServiceComponent(Component.QUERY_API)
public class StagingDcsQueryApi {

    private static final String CASE_ID = "caseId";
    private static final String DEFENDANT_ID = "defendantId";
    private static final String DEFENDANT_STATUS = "defendantStatus";
    private static final String CASE_STATUS = "caseStatus";
    private static final String DEFENDANTS = "defendants";
    private static final String LATEST_DEFENDANT_OPERATIONS = "latestDefendantOperations";
    private static final String TRANSACTION_ID = "transactionId";
    private static final String STATUS = "status";
    private static final String TYPE = "type";
    private static final String UPDATED_TIME = "updatedTime";

    @Inject
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Inject
    private TransactionMetadataRepository transactionMetadataRepository;

    @Handles("stagingdcs.query.dcscase-status-by-case-id")
    public JsonEnvelope fetchCaseDetailByCaseId(final JsonEnvelope envelope) {
        final UUID caseId = UUID.fromString(envelope.payloadAsJsonObject().getString(CASE_ID));

        final JsonObjectBuilder jsonObjectBuilder = JsonObjects.createObjectBuilder();
        jsonObjectBuilder.add(CASE_ID, caseId.toString());

        List<DcsCaseDetailEntity> dcsCaseDetails = dcsCaseDetailRepository.findByCaseId(caseId);

        if (CollectionUtils.isNotEmpty(dcsCaseDetails)) {
            mapCaseDetailsToJson(dcsCaseDetails, jsonObjectBuilder);
        }
        return envelopeFrom(envelope.metadata(), jsonObjectBuilder.build());
    }

    @Handles("stagingdcs.query.dcscase-status-by-case-id-defendant-id")
    public JsonEnvelope fetchCaseDetailByCaseIdDefendantId(final JsonEnvelope envelope) {

        final UUID caseId = UUID.fromString(envelope.payloadAsJsonObject().getString(CASE_ID));
        final UUID defendantId = UUID.fromString(envelope.payloadAsJsonObject().getString(DEFENDANT_ID));

        final JsonObjectBuilder jsonObjectBuilder = JsonObjects.createObjectBuilder();
        jsonObjectBuilder.add(CASE_ID, caseId.toString());

        DcsCaseDetailEntity dcsCaseDetailEntity = dcsCaseDetailRepository.findByCaseIdDefendantId(caseId, defendantId);

        if (dcsCaseDetailEntity != null && isCaseDefendantStatusTerminal(dcsCaseDetailEntity.getDcsDefendantStatus())) {
            mapCaseDetailsToJson(List.of(dcsCaseDetailEntity), jsonObjectBuilder);
        }
        return envelopeFrom(envelope.metadata(), jsonObjectBuilder.build());
    }

    private JsonObjectBuilder mapCaseDetailsToJson(final List<DcsCaseDetailEntity> dcsCaseDetails, final JsonObjectBuilder jsonObjectBuilder) {

        final JsonArrayBuilder defendantsArrayBuilder = JsonObjects.createArrayBuilder();
        dcsCaseDetails.stream()
                .filter(dcsCaseDetailEntity -> isCaseDefendantStatusTerminal(dcsCaseDetailEntity.getDcsDefendantStatus()))
                .forEach(dcsCaseDetailEntity -> mapCaseDetailsToResponse(dcsCaseDetails, jsonObjectBuilder, defendantsArrayBuilder, dcsCaseDetailEntity));

        jsonObjectBuilder.add(DEFENDANTS, defendantsArrayBuilder);
        return jsonObjectBuilder;
    }

    private void mapCaseDetailsToResponse(final List<DcsCaseDetailEntity> dcsCaseDetails, final JsonObjectBuilder jsonObjectBuilder, final JsonArrayBuilder defendantsArrayBuilder, final DcsCaseDetailEntity dcsCaseDetailEntity) {
        final JsonObjectBuilder defendantBuilder = JsonObjects.createObjectBuilder();
        defendantBuilder.add(DEFENDANT_ID, dcsCaseDetailEntity.getDefendantId().toString());
        defendantBuilder.add(DEFENDANT_STATUS, mapDefendantStatus(dcsCaseDetailEntity));

        final JsonArrayBuilder operationsArrayBuilder = JsonObjects.createArrayBuilder();
        List<TransactionMetadataEntity> transactionMetaDataList = transactionMetadataRepository.findByCaseIdAndDefendantId(
                dcsCaseDetailEntity.getCaseId(), dcsCaseDetailEntity.getDefendantId());

        Optional.ofNullable(transactionMetaDataList)
                .filter(transactionsList -> !transactionsList.isEmpty())
                .ifPresent(transactionsList -> transactionsList.stream()
                        .filter(transaction -> (TransactionType.DEFENDANT_UPDATE.name().equals(transaction.getTransactionType())
                                || TransactionType.DEFENCE_REPRESENTATION.name().equals(transaction.getTransactionType())) && isTerminalOperation(transaction))
                        .collect(Collectors.groupingBy(
                                TransactionMetadataEntity::getTransactionType,
                                Collectors.maxBy(Comparator.comparing(TransactionMetadataEntity::getUpdatedAt))
                        ))
                        .values()
                        .stream()
                        .flatMap(Optional::stream)
                        .forEach(transaction -> {
                            final JsonObjectBuilder operationBuilder = JsonObjects.createObjectBuilder();
                            operationBuilder.add(TRANSACTION_ID, transaction.getTransactionRefId().toString());
                            operationBuilder.add(STATUS, mapTransactionStatus(transaction.getTransactionStatus()));
                            operationBuilder.add(TYPE, transaction.getTransactionType());
                            operationBuilder.add(UPDATED_TIME, transaction.getUpdatedAt().format(ZONE_DATETIME_FORMATTER).toString());
                            operationsArrayBuilder.add(operationBuilder);
                        }));

        jsonObjectBuilder.add(CASE_STATUS, mapCaseStatus(dcsCaseDetails));

        defendantBuilder.add(LATEST_DEFENDANT_OPERATIONS, operationsArrayBuilder);
        defendantsArrayBuilder.add(defendantBuilder);
    }

    private boolean isTerminalOperation(final TransactionMetadataEntity transaction) {
        return TransactionStatus.SUCCESS.name().equals(transaction.getTransactionStatus())
                || TransactionStatus.FAILED.name().equals(transaction.getTransactionStatus());
    }

    private boolean isCaseDefendantStatusTerminal(final String caseDetailEntityStatus) {
        List<String> terminalStatusList = List.of(DcsDefendantStatus.FAILED.name(), DcsDefendantStatus.LINKED.name(), DcsDefendantStatus.UNLINKED.name());
        return terminalStatusList.contains(caseDetailEntityStatus);
    }
}

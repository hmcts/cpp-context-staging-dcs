package uk.gov.moj.cpp.staging.dcs.event.jobstore.service;

import static java.util.Objects.nonNull;
import static java.util.UUID.fromString;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections.MapUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import uk.gov.hmcts.dcs.openapi.model.LinkCaseAndDefendantRequest;
import uk.gov.hmcts.dcs.openapi.model.RequestFulfilledResponsePayload;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionMetadataEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;

public class DcsNotificationHelper {
    @Inject
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Inject
    private TransactionDetailRepository transactionDetailRepository;

    @Inject
    private TransactionMetadataRepository transactionMetadataRepository;
    @Inject
    public Logger logger;

    public void updateDcsCaseDetail(final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest, final RequestFulfilledResponsePayload requestFulfilledResponsePayload){
        final String caseReferral = requestFulfilledResponsePayload.getCaseReferral();

        linkCaseAndDefendantRequest.getDefendants().forEach(defendant -> {
            UUID caseId = fromString(linkCaseAndDefendantRequest.getCaseId());
            UUID defendantId = fromString(defendant.getId());

            DcsCaseDetailEntity dcsCaseDetailEntity = dcsCaseDetailRepository.findByCaseIdDefendantId(caseId, defendantId);
            requestFulfilledResponsePayload.getDefendants().stream()
                    .forEach(responseDefendant -> {
                        if (responseDefendant.getDefendantId().equals(defendant.getId())) {
                            dcsCaseDetailRepository.updateTransactionById(fromString(caseReferral),
                                    fromString(responseDefendant.getDefendantReferral()), DcsDefendantStatus.LINKED.toString(), dcsCaseDetailEntity.getId());
                        }
                    });
        });
    }

    public void saveOrUpdateTransactionDetails(final UUID transactionRefId, final UUID caseId, final String payload, final String status, final String error, final TransactionType transactionType) {

        TransactionDetailEntity transactionDetailEntity = transactionDetailRepository.findByTransactionReferenceId(transactionRefId);
        if (nonNull(transactionDetailEntity) && nonNull(transactionDetailEntity.getTransactionRefId())) {
            String dbError = isEmpty(error) ? EMPTY : error;
            transactionDetailRepository.updateStatusByTransactionReferenceId(status, dbError, transactionRefId);
            return;
        }
        transactionDetailEntity = new TransactionDetailEntity();
        transactionDetailEntity.setTransactionRefId(transactionRefId);
        transactionDetailEntity.setCaseId(caseId);
        transactionDetailEntity.setPayload(payload);
        transactionDetailEntity.setCreatedAt(ZonedDateTime.now());
        transactionDetailEntity.setTransactionType(transactionType.toString());
        if (nonNull(status)) {
            transactionDetailEntity.setTransactionStatus(status);
        }

        if (nonNull(error)) {
            transactionDetailEntity.setError(error);
        }
        transactionDetailRepository.save(transactionDetailEntity);
        logger.info("Transaction details saved successfully. transactionRefId: {}, caseId: {}", transactionRefId, caseId);
    }

    public void saveOrUpdateMetadata(final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest, final String status) {
        final UUID transactionReference = fromString(linkCaseAndDefendantRequest.getTransactionRef());
        linkCaseAndDefendantRequest.getDefendants().stream()
                .forEach(defendant -> saveOrUpdateTransactionMetadata(transactionReference, fromString(linkCaseAndDefendantRequest.getCaseId()),
                        fromString(defendant.getId()), status, TransactionType.LINK_DEFENDANT, null));
    }

    public void saveOrUpdateMaterialMetadata(final MaterialTaskData taskData, final String status) {
        if (isNotEmpty(taskData.getDefendantIdReferralIdMap())) {
            taskData.getDefendantIdReferralIdMap()
                    .forEach((k, v) -> saveOrUpdateTransactionMetadata(fromString(taskData.getTranRefId()), fromString(taskData.getCaseId()), fromString(k), status, TransactionType.MATERIAL_UPDATE, fromString(taskData.getMaterialId())));
        } else {
            saveOrUpdateTransactionMetadata(fromString(taskData.getTranRefId()), fromString(taskData.getCaseId()), null, status, TransactionType.MATERIAL_UPDATE, fromString(taskData.getMaterialId()));
        }
    }

    public void saveOrUpdateTransactionMetadata(final UUID transactionRefId, final UUID caseId, final UUID defendantId, final String status, final TransactionType transactionType, final UUID materialId) {

        List<TransactionMetadataEntity> transactionMetadataEntityList = transactionMetadataRepository.findByTransactionReferenceId(transactionRefId);

        if (isNotEmpty(transactionMetadataEntityList) && isTransactionEligibleForUpdateOnly(transactionMetadataEntityList, caseId, defendantId, materialId, transactionRefId, transactionType)) {
            transactionMetadataRepository.updateStatusByTransactionReferenceId(status, transactionRefId);
            return;
        }

        TransactionMetadataEntity transactionMetadataEntity = new TransactionMetadataEntity();
        transactionMetadataEntity.setId(UUID.randomUUID());
        transactionMetadataEntity.setTransactionRefId(transactionRefId);
        transactionMetadataEntity.setCaseId(caseId);
        transactionMetadataEntity.setDefendantId(defendantId);
        transactionMetadataEntity.setTransactionType(transactionType.toString());
        transactionMetadataEntity.setCreatedAt(ZonedDateTime.now());
        transactionMetadataEntity.setUpdatedAt(ZonedDateTime.now());
        transactionMetadataEntity.setMaterialId(materialId);

        if (nonNull(status)) {
            transactionMetadataEntity.setTransactionStatus(status);
        }
        transactionMetadataRepository.save(transactionMetadataEntity);
        logger.info("Transaction metadata saved successfully. transactionRefId: {}, caseId: {}", transactionRefId, caseId);
    }

    public static String getKeyByValue(Map<String, String> map, String value) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (Objects.equals(entry.getValue(), value)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @SuppressWarnings("java:S1067")
    private boolean isTransactionEligibleForUpdateOnly(final List<TransactionMetadataEntity> transactionMetadataList, final UUID caseId, final UUID defendantId, final UUID materialId, final UUID transactionRefId, final TransactionType transactionType) {
        return transactionMetadataList.stream()
                .anyMatch(transactionMetadata -> nonNull(transactionMetadata)
                        && Objects.equals(transactionMetadata.getTransactionType(), transactionType.toString())
                        && Objects.equals(transactionMetadata.getCaseId(), caseId)
                        && Objects.equals(transactionMetadata.getDefendantId(), defendantId)
                        && Objects.equals(transactionMetadata.getMaterialId(), materialId)
                        && Objects.equals(transactionMetadata.getTransactionRefId(), transactionRefId));
    }

}

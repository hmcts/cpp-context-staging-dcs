package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections.MapUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus.LINKED;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsNotificationHelper.getKeyByValue;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.INITIATE_MATERIAL_TASK_FOR_CASE;
import static uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper.getAllowedFileExtension;
import static uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper.getDocumentName;

import uk.gov.justice.core.courts.CourtDocument;
import uk.gov.justice.core.courts.CourtDocumentIndex;
import uk.gov.justice.core.courts.Material;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.moj.cpp.jobstore.api.annotation.Task;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.domain.common.Constants;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsResponseProcessingException;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.ProgressionService;
import uk.gov.moj.cpp.staging.dcs.event.service.ReferenceDataService;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsDefendantEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsDefendantRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.NotFoundException;

@SuppressWarnings({"squid:S6813", "squid:S2629"})
@Task(INITIATE_MATERIAL_TASK_FOR_CASE)
public class InitiateCaseMaterialSubmissionTask extends BaseTask implements ExecutableTask {

    @Inject
    private DcsOperationHelper dcsOperationHelper;

    @Inject
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Inject
    private ProgressionService progressionService;

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Inject
    private DcsDefendantRepository dcsDefendantRepository;

    @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        return Optional.of(retryConfiguration.getTaskRetryDurationsSeconds());
    }

    @Override
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {
        final JsonObject jobData = executionInfo.getJobData();
        final MaterialTaskData taskData = jsonObjectToObjectConverter.convert(jobData, MaterialTaskData.class);
        final String caseId = taskData.getCaseId();
        logger.info("Starting INITIATE_MATERIAL_TASK_FOR_CASE for caseId: {}", caseId);
        String caseUrn;
        final Map<String, String> defendantReferralIdMap = taskData.getDefendantIdReferralIdMap();


        try {
            // checking which defendants are linked
            final List<DcsCaseDetailEntity> allCaseEntities = dcsCaseDetailRepository.findByCaseId(fromString(caseId));
            final List<UUID> linkedDefendantIds = getDefendantIdList(allCaseEntities);
            final Map<String, String> currentlyLinkedDefendantReferralIdMap = new HashMap<>();
            linkedDefendantIds.stream().forEach(jobLinkedDefendantId -> {
                if (defendantReferralIdMap.containsKey(jobLinkedDefendantId.toString())) {
                    currentlyLinkedDefendantReferralIdMap.put(jobLinkedDefendantId.toString(), defendantReferralIdMap.get(jobLinkedDefendantId.toString()));
                }
            });

            if (isEmpty(linkedDefendantIds) || isEmpty(currentlyLinkedDefendantReferralIdMap)) {
                logger.error("No linked defendant present found for caseId: {}", caseId);
                throw new NotFoundException(format("No linked defendant present found for case %s", caseId));
            }

            final List<CourtDocumentIndex> courtDocumentIndexList = progressionService.getCourtDocumentsByParams(caseId, null).getDocumentIndices();
            if (isEmpty(courtDocumentIndexList)) {
                logger.error("No court documents are present for caseId: {}", caseId);
                throw new NotFoundException(format("No court documents are present for the caseId %s", caseId));
            }

            final Map<String, String> masterDefendantIdMap = new HashMap<>();
            if (!isEmpty(currentlyLinkedDefendantReferralIdMap)) {
                currentlyLinkedDefendantReferralIdMap.keySet().stream().forEach(linkedDefId -> {
                    final DcsDefendantEntity dcsDefendantEntity = dcsDefendantRepository.findByDefendantId(UUID.fromString(linkedDefId));
                    if (nonNull(dcsDefendantEntity.getMasterDefendantId())) {
                        logger.info("-----Updating masterDefendantIdMap------{}: master def id :{}", linkedDefId, dcsDefendantEntity.getMasterDefendantId());
                        masterDefendantIdMap.put(linkedDefId, dcsDefendantEntity.getMasterDefendantId().toString());
                    }
                });
            }

            caseUrn = allCaseEntities.get(0).getCaseUrn();
            //getting all the court document sections and creating task for each document.
            final Map<String, Boolean> documentTypeAccessMap = referenceDataService.createDocumentAccessTypeMap();
            List<MaterialTaskData> eligibleTasks = buildMaterialTaskData(caseId, taskData.getCaseReferralId(), caseUrn, currentlyLinkedDefendantReferralIdMap,
                    courtDocumentIndexList, documentTypeAccessMap, masterDefendantIdMap);
            // initiate check material status task
            eligibleTasks.forEach(task -> {
                if (dcsOperationHelper.shouldSendMaterialToDcs(task)) {
                    dcsOperationHelper.processInsertMaterialDocument(objectToJsonObjectConverter.convert(task));
                }
            });
        } catch (Exception e) {
            logger.debug("Exception while processing  task INITIATE_MATERIAL_TASK_FOR_CASE for caseId: {}, {}", caseId, e.getMessage());
            return retryTask(e.getMessage());
        }
        return dcsOperationHelper.returnCompletedExecutionInfo();
    }

    private List<UUID> getDefendantIdList(final List<DcsCaseDetailEntity> entityList) {
        return entityList.stream()
                .filter(dcsCaseDetailEntity -> (dcsCaseDetailEntity.getDcsDefendantStatus().equalsIgnoreCase(LINKED.toString())))
                .map(DcsCaseDetailEntity::getDefendantId)
                .toList();
    }

    private List<MaterialTaskData> buildMaterialTaskData(final String caseId, final String caseReferral, final String caseUrn, final Map<String, String> currentlyLinkedDefendantReferralIdMap, final List<CourtDocumentIndex> courtDocumentIndexList, final Map<String, Boolean> documentTypeAccessMap, final Map<String, String> masterDefendantIdMap) {
        return courtDocumentIndexList.stream()
                .filter(documentIndex -> nonNull(documentIndex.getDocument())
                        && nonNull(documentIndex.getDocument().getDocumentTypeId())
                        && documentTypeAccessMap.get(documentIndex.getDocument().getDocumentTypeId().toString()))
                .filter(documentIndex -> isDefendantLevelMaterialBelongsToLinkedDefendant(documentIndex, masterDefendantIdMap))
                .filter(documentIndex -> isNotEmpty(getAllowedFileExtension(documentIndex.getDocument().getMimeType())))
                .map(documentIndex -> mapDocumentIndexToMaterialData(caseId, caseUrn, caseReferral, currentlyLinkedDefendantReferralIdMap, documentIndex, masterDefendantIdMap))
                .toList();
    }

    private boolean isDefendantLevelMaterialBelongsToLinkedDefendant(final CourtDocumentIndex index, final Map<String, String> masterDefendantIdMap) {
        if (isNotEmpty(index.getDefendantIds())) {
            return index.getDefendantIds().stream()
                    .anyMatch(materialDefendantId -> isLinkedDefendantId(materialDefendantId, masterDefendantIdMap));
        }
        return true;
    }

    private MaterialTaskData mapDocumentIndexToMaterialData(final String caseId, final String caseUrn, final String caseReferral, final Map<String, String> currentlyLinkedDefendantReferralIdMap, final CourtDocumentIndex courtDocumentIndex, final Map<String, String> masterDefendantIdMap) {
        MaterialTaskData data = new MaterialTaskData();
        data.setCaseId(caseId);
        data.setCaseUrn(caseUrn);
        data.setCaseReferralId(caseReferral);
        setDocumentData(data, currentlyLinkedDefendantReferralIdMap, courtDocumentIndex, masterDefendantIdMap);
        final UUID transactionRefId = randomUUID();
        data.setTranRefId(transactionRefId.toString());
        return data;
    }

    private void setDocumentData(final MaterialTaskData data, final Map<String, String> currentlyLinkedDefendantReferralIdMap, final CourtDocumentIndex courtDocumentIndex, final Map<String, String> masterDefendantIdMap) {
        data.setDocumentSection(courtDocumentIndex.getType());
        if (isEmpty(courtDocumentIndex.getDefendantIds())) {
            data.setCaseLevel(true);
        }

        if (isNotEmpty(courtDocumentIndex.getDefendantIds())) {
            data.setDefendantLevel(true);
            Map<String, String> defendantsForThisDocument = new HashMap<>();
            courtDocumentIndex.getDefendantIds().forEach(defId -> {
                final String defendantId = getLinkedDefendantId(defId, masterDefendantIdMap);
                if (nonNull(defendantId) && currentlyLinkedDefendantReferralIdMap.containsKey(defendantId)) {
                    defendantsForThisDocument.put(defendantId, currentlyLinkedDefendantReferralIdMap.get(defendantId));
                }
            });
            data.setDefendantIdReferralIdMap(defendantsForThisDocument);
        }

        if (nonNull(courtDocumentIndex.getDocument()) && isNotEmpty(courtDocumentIndex.getDocument().getMaterials())) {
            final CourtDocument document = courtDocumentIndex.getDocument();
            final Material material = document.getMaterials().get(0);
            data.setDocumentTypeAccessId(document.getDocumentTypeId().toString());
            data.setMaterialId(material.getId().toString());
            data.setDocumentName(getDocumentName(material, document));
            data.setDocumentDate(material.getUploadDateTime().toLocalDate().format(Constants.SIMPLE_DATE_FORMAT));
        }
    }

    private ExecutionInfo retryTask(final String responseErr) {
        final UUID retryTranRefId = randomUUID();
        logger.info(format("retrying the INITIATE_MATERIAL_TASK_FOR_CASE with new retryID %s", retryTranRefId));
        return getRetryExecutionInfo(new DcsResponseProcessingException(responseErr), retryTranRefId.toString(), INITIATE_MATERIAL_TASK_FOR_CASE);
    }

    private boolean isLinkedDefendantId(final UUID defendantId, final Map<String, String> masterDefendantIdMap) {
        return masterDefendantIdMap.containsKey(defendantId.toString())
                || masterDefendantIdMap.containsValue(defendantId.toString());
    }

    private String getLinkedDefendantId(final UUID defendantId, final Map<String, String> masterDefendantIdMap) {
        if (masterDefendantIdMap.containsKey(defendantId.toString())) {
            return defendantId.toString();
        }
        return getKeyByValue(masterDefendantIdMap, defendantId.toString());
    }
}

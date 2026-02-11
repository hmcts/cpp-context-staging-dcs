package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections.MapUtils.isEmpty;
import static uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus.LINKED;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsNotificationHelper.getKeyByValue;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.PROCESS_ADD_COURT_DOCUMENT_TASK;
import static uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper.getAllowedFileExtension;
import static uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper.getDocumentName;
import static uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper.isDefendantLevel;

import uk.gov.justice.core.courts.CourtDocument;
import uk.gov.justice.core.courts.Material;
import uk.gov.justice.core.courts.NowDocument;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.moj.cpp.jobstore.api.annotation.Task;
import uk.gov.moj.cpp.jobstore.api.task.ExecutableTask;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsResponseProcessingException;
import uk.gov.moj.cpp.staging.dcs.event.service.DcsOperationHelper;
import uk.gov.moj.cpp.staging.dcs.event.service.ReferenceDataService;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsDefendantEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsDefendantRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.NotFoundException;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

@SuppressWarnings({"squid:S6813", "squid:S2629"})
@Task(PROCESS_ADD_COURT_DOCUMENT_TASK)
public class ProcessAddCourtDocumentTask extends BaseTask implements ExecutableTask {

    public static final String SEND_TO_DCS = "sendToDcs";
    public static final String SECTION = "section";

    public static final String ID = "id";

    @Inject
    private DcsOperationHelper dcsOperationHelper;

    @Inject
    private DcsCaseDetailRepository dcsCaseDetailRepository;

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
    @SuppressWarnings("java:S1142")
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {
        logger.info("Starting PROCESS_ADD_COURT_DOCUMENT_TASK ..");
        final JsonObject jobData = executionInfo.getJobData();
        try {
            final CourtDocument courtDocument = jsonObjectToObjectConverter.convert(jobData, CourtDocument.class);
            UUID caseId = getCaseId(courtDocument);
            UUID courtDocumentId = courtDocument.getCourtDocumentId();

            if (isNull(caseId)) {
                logger.info("No linked caseId found for material in court document {}", courtDocumentId.toString());
                throw new NotFoundException(format("linked case not found for document %s, hence will retry the task", courtDocumentId));
            }

            final List<DcsCaseDetailEntity> linkedCaseEntities = getCaseLinkedEntities(caseId);
            if (isEmpty(linkedCaseEntities)) {
                logger.info("No linked caseId {} found for material in court document {}", caseId, courtDocumentId);
                return dcsOperationHelper.returnCompletedExecutionInfo();
            }

            Map<String, String> defendantReferralIdMap = null;
            final boolean isMaterialDefendantLevel = isDefendantLevel(courtDocument);
            if (isMaterialDefendantLevel) {
                List<UUID> eventDefendants = getEventDefendants(courtDocument);
                final Map<String, String> masterDefendantIdMap = getMasterDefendantIdMap(linkedCaseEntities);
                defendantReferralIdMap = getDefendantLevelMap(eventDefendants, linkedCaseEntities, masterDefendantIdMap);
            }

            if ((isMaterialDefendantLevel && isEmpty(defendantReferralIdMap))) {
                return dcsOperationHelper.returnCompletedExecutionInfo();
            }

            UUID materialId = courtDocument.getMaterials().get(0).getId();
            if (StringUtils.isEmpty(getAllowedFileExtension(courtDocument.getMimeType()))) {
                logger.info("No allowed file extension found for caseId {} and materialId {} hence skipping sending to Dcs", caseId, materialId);
                return dcsOperationHelper.returnCompletedExecutionInfo();
            }

            final Optional<JsonObject> documentAccessType = referenceDataService.getDocumentTypeAccessById(courtDocument.getDocumentTypeId().toString());
            if (documentAccessType.isEmpty() || !documentAccessType.get().getBoolean(SEND_TO_DCS, false)) {
                logger.info("No caseId {} linked for material in event public.progression.events.court-document-created or document section not found for documentAccessId {}", caseId, courtDocument.getDocumentTypeId());
                return dcsOperationHelper.returnCompletedExecutionInfo();
            }

            final MaterialTaskData taskData = buildMaterialTaskData(caseId.toString(), linkedCaseEntities.get(0).getCaseRefId().toString(),
                    linkedCaseEntities.get(0).getCaseUrn(), defendantReferralIdMap,
                    courtDocument, documentAccessType.get());

            if (dcsOperationHelper.shouldSendMaterialToDcs(taskData)) {
                dcsOperationHelper.processInsertMaterialDocument(objectToJsonObjectConverter.convert(taskData));
            }
        } catch (Exception e) {
            logger.debug("Exception while processing  task PROCESS_ADD_COURT_DOCUMENT_TASK with : {}", e.getMessage());
            return retryTask(e.getMessage());
        }
        return dcsOperationHelper.returnCompletedExecutionInfo();
    }

    private ExecutionInfo retryTask(final String responseErr) {
        final UUID retryTranRefId = randomUUID();
        logger.info("retrying the PROCESS_ADD_COURT_DOCUMENT_TASK with new retryID {}", retryTranRefId);
        return getRetryExecutionInfo(new DcsResponseProcessingException(responseErr), retryTranRefId.toString(), PROCESS_ADD_COURT_DOCUMENT_TASK);
    }

    private UUID getCaseId(final CourtDocument courtDocument) {
        UUID caseId = null;
        if (nonNull(courtDocument.getDocumentCategory().getCaseDocument())) {
            caseId = courtDocument.getDocumentCategory().getCaseDocument().getProsecutionCaseId();
        }

        if (nonNull(courtDocument.getDocumentCategory().getDefendantDocument())) {
            caseId = courtDocument.getDocumentCategory().getDefendantDocument().getProsecutionCaseId();
        }

        if (nonNull(courtDocument.getDocumentCategory().getNowDocument())) {
            final NowDocument document = courtDocument.getDocumentCategory().getNowDocument();
            if (document.getProsecutionCases().size() == 1) {
                caseId = document.getProsecutionCases().get(0);
            } else {
                caseId = getCaseIdFromMultipleCaseIds(document.getProsecutionCases(), document.getDefendantId());
            }
        }
        return caseId;
    }

    private List<UUID> getEventDefendants(final CourtDocument courtDocument) {
        List<UUID> eventDefendants = new ArrayList<>();
        if (nonNull(courtDocument.getDocumentCategory().getDefendantDocument())) {
            eventDefendants = courtDocument.getDocumentCategory().getDefendantDocument().getDefendants();
        }

        if (nonNull(courtDocument.getDocumentCategory().getNowDocument())) {
            final NowDocument document = courtDocument.getDocumentCategory().getNowDocument();
            if (nonNull(document.getDefendantId())) {
                eventDefendants = Collections.singletonList(document.getDefendantId());
            }
        }
        return eventDefendants;
    }

    private List<DcsCaseDetailEntity> getCaseLinkedEntities(final UUID caseId) {
        final List<DcsCaseDetailEntity> allCaseEntities = dcsCaseDetailRepository.findByCaseId(caseId);
        return allCaseEntities.stream()
                .filter(entity -> LINKED.name().equalsIgnoreCase(entity.getDcsDefendantStatus()))
                .toList();
    }

    private MaterialTaskData buildMaterialTaskData(final String caseId, final String caseReferralId, final String caseUrn, final Map<String, String> defendantReferralIdMap,
                                                   final CourtDocument courtDocument, final JsonObject documentTypeAccess) {
        MaterialTaskData taskData = new MaterialTaskData();
        taskData.setCaseId(caseId);
        taskData.setCaseReferralId(caseReferralId);
        taskData.setCaseUrn(caseUrn);

        if (MapUtils.isNotEmpty(defendantReferralIdMap)) {
            taskData.setDefendantIdReferralIdMap(defendantReferralIdMap);
            taskData.setDefendantLevel(true);
        } else {
            taskData.setCaseLevel(true);
        }

        taskData.setDocumentTypeAccessId(documentTypeAccess.getString(ID));
        taskData.setDocumentSection(documentTypeAccess.getString(SECTION));

        if (isNotEmpty(courtDocument.getMaterials())) {
            Material material = courtDocument.getMaterials().get(0);
            if (nonNull(material.getUploadDateTime())) {
                taskData.setDocumentDate(material.getUploadDateTime().toLocalDate().toString());
            }
            taskData.setMaterialId(material.getId().toString());
            taskData.setDocumentName(getDocumentName(material, courtDocument));
            taskData.setMaterialId(material.getId().toString());
        }

        final UUID transactionRefId = randomUUID();
        taskData.setTranRefId(transactionRefId.toString());

        return taskData;
    }

    private Map<String, String> getDefendantLevelMap(final List<UUID> eventDefendants, final List<DcsCaseDetailEntity> allCaseEntities, final Map<String, String> masterDefendantIdMap) {
        final Map<String, String> defendantReferralIdMap = new HashMap<>();
        eventDefendants.forEach(eventDefendant ->
                allCaseEntities.stream()
                        .filter(entity -> DcsDefendantStatus.LINKED.name().equalsIgnoreCase(entity.getDcsDefendantStatus()))
                        .filter(entity -> isMaterialMasterDefendantIsLinkedDefendant(eventDefendant, entity, masterDefendantIdMap))
                        .forEach(entity -> defendantReferralIdMap.put(entity.getDefendantId().toString(), entity.getDefendantRefId().toString()))
        );
        return defendantReferralIdMap;
    }

    private boolean isMaterialMasterDefendantIsLinkedDefendant(final UUID eventId,
                                                               final DcsCaseDetailEntity entity, final Map<String, String> masterDefendantIdMap) {
        final String keyByMasterId = getKeyByValue(masterDefendantIdMap, eventId.toString());

        final boolean matchesMaster = keyByMasterId != null
                && keyByMasterId.equalsIgnoreCase(entity.getDefendantId().toString());

        final boolean matchesDefendant = masterDefendantIdMap.containsKey(eventId.toString())
                && eventId.equals(entity.getDefendantId());

        return matchesMaster || matchesDefendant;
    }

    private UUID getCaseIdFromMultipleCaseIds(final List<UUID> prosecutionCases, final UUID defendantId) {
        final List<DcsCaseDetailEntity> caseDetailEntities = dcsCaseDetailRepository.findByDefendantId(defendantId);
        return prosecutionCases.stream()
                .filter(caseId -> caseDetailEntities.stream()
                        .filter(entity -> LINKED.name().equalsIgnoreCase(entity.getDcsDefendantStatus()))
                        .anyMatch(entity -> entity.getCaseId().equals(caseId)))
                .findFirst().orElse(null);
    }

    private Map<String, String> getMasterDefendantIdMap(final List<DcsCaseDetailEntity> allCaseEntities){
        final Map<String, String> masterDefendantIdMap = new HashMap<>();
        allCaseEntities.stream().forEach(dcsCaseDetailEntity -> {
            final DcsDefendantEntity dcsDefendantEntity = dcsDefendantRepository.findByDefendantId(dcsCaseDetailEntity.getDefendantId());
            if (nonNull(dcsDefendantEntity.getMasterDefendantId())) {
                masterDefendantIdMap.put(dcsCaseDetailEntity.getDefendantId().toString(), dcsDefendantEntity.getMasterDefendantId().toString());
            }
        });

        return masterDefendantIdMap;
    }
}

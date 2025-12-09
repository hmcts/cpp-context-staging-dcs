package uk.gov.moj.cpp.staging.dcs.event.service;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.fromString;
import static javax.transaction.Transactional.TxType.REQUIRES_NEW;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections.MapUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.hmcts.cp.openapi.model.ErrorPayload.ErrorCodeEnum.CASE_DELETED;
import static uk.gov.hmcts.cp.openapi.model.ErrorPayload.ErrorCodeEnum.CASE_HAS_SPLIT_OR_MERGED;
import static uk.gov.hmcts.cp.openapi.model.ErrorPayload.ErrorCodeEnum.DEFENDANT_DELETED;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo.executionInfo;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.COMPLETED;
import static uk.gov.moj.cpp.jobstore.api.task.ExecutionStatus.STARTED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.Constants.DOCUMENT_NAME;
import static uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus.LINKED;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.SENT;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus.SUCCESS;
import static uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType.MATERIAL_UPDATE;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.CHECK_MATERIAL_STATUS_TASK;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.INITIATE_MATERIAL_TASK_FOR_CASE;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.INSERT_MATERIAL_DOCUMENT_TASK;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.PROCESS_ADD_COURT_DOCUMENT_TASK;
import static uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks.DcsRequestTaskNames.UPLOAD_MATERIAL_TO_STORAGE_TASK;

import uk.gov.hmcts.cp.openapi.model.ErrorPayload;
import uk.gov.hmcts.cp.openapi.model.UpdateTransactionStatusRequest;
import uk.gov.justice.core.courts.CourtDocument;
import uk.gov.justice.core.courts.Material;
import uk.gov.justice.core.courts.NowDocument;
import uk.gov.justice.services.common.configuration.GlobalValue;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.moj.cpp.jobstore.api.ExecutionService;
import uk.gov.moj.cpp.jobstore.api.task.ExecutionInfo;
import uk.gov.moj.cpp.jobstore.persistence.Priority;
import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.pojo.MaterialTaskData;
import uk.gov.moj.cpp.staging.dcs.event.jobstore.service.DcsResponseProcessingException;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.CaseDocumentEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DefendantDocumentEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.CaseDefendantOffencesRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.CaseDocumentRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DefendantDocumentRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.transaction.Transactional;
import javax.ws.rs.NotFoundException;

import org.slf4j.Logger;

@Transactional
@SuppressWarnings("java:S2629")
public class DcsOperationHelper {

    public static final String DOT_STRING = ".";
    public static final String FORWARD_SLASH = "/";
    public static final String MIME_TYPE_FOR_TEXT_EXTENSION = "text/plain";
    public static final String MIME_TYPE_FOR_DOC_EXTENSION = "application/msword";
    public static final String MIME_TYPE_FOR_DOCX_EXTENSION = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String DOT_TXT = ".txt";
    public static final String DOT_DOC = ".doc";
    public static final String DOT_DOCX = ".docx";
    public static final String STRING_NO_DISTRIBUTION = "No Distribution";
    @Inject
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    @Inject
    private CaseDefendantOffencesRepository caseDefendantOffencesRepository;
    @Inject
    private UtcClock clock;

    @Inject
    private ExecutionService executionService;

    @Inject
    private TransactionMetadataRepository transactionMetadataRepository;

    @Inject
    private TransactionDetailRepository transactionDetailRepository;

    @Inject
    private AzureStorageService azureStorageService;

    @Inject
    private CaseDocumentRepository caseDocumentRepository;

    @Inject
    private DefendantDocumentRepository defendantDocumentRepository;

    @Inject
    public Logger logger;

    @Inject
    @GlobalValue(key = "stagingdcs.material.tasks.delay.seconds", defaultValue = "20")
    public String materialTasksDelayInSeconds;

    private StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();

    private static final List<String> knownUnlinkErrors = Arrays.asList(CASE_HAS_SPLIT_OR_MERGED.getValue(), CASE_DELETED.getValue(), DEFENDANT_DELETED.getValue());

    public void unlinkByCaseIdIfErrorsPresent(String errorMessage, UUID caseId) {
        if (isNotEmpty(errorMessage)) {
            for(String error: knownUnlinkErrors) {
                if(errorMessage.contains(error)) {
                    unlinkByCaseId(caseId);
                }
            }
        }
    }

    public void unlinkByCaseId(final UUID caseId) {
        caseDefendantOffencesRepository.deleteAllByCaseId(caseId);
        List<DcsCaseDetailEntity> allRecords = dcsCaseDetailRepository.findByCaseId(caseId);
        final List<UUID> caseDetailsRowIds = allRecords.stream()
                .filter(entity -> isCaseStatusEligibleForUnlink(entity.getDcsDefendantStatus()))
                .map(entity -> entity.getId())
                .toList();

        if (isNotEmpty(caseDetailsRowIds)) {
            final ZonedDateTime updatedAt = ZonedDateTime.now();
            dcsCaseDetailRepository.updateDcsCaseDetailStatusByIds(DcsDefendantStatus.UNLINKED.name(), updatedAt, caseDetailsRowIds);
        }
    }

    public boolean isCaseLinked(final UUID caseId) {
        final List<DcsCaseDetailEntity> dcsCaseDetailEntities = dcsCaseDetailRepository.findByCaseId(caseId);

        final Optional<DcsCaseDetailEntity> entity = dcsCaseDetailEntities.stream()
                .filter(dcsCaseDetailEntity -> (dcsCaseDetailEntity.getDcsDefendantStatus().equalsIgnoreCase(LINKED.toString())))
                .findAny();

        return entity.isPresent();
    }
    public ExecutionInfo returnCompletedExecutionInfo(){
        return executionInfo()
                .withExecutionStatus(COMPLETED)
                .build();
    }


    public void initiateUploadToStorageTask(final JsonObject inputJsonObject) {
        logger.info("Initiate initiateUploadToStorageTask..");
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                inputJsonObject,
                UPLOAD_MATERIAL_TO_STORAGE_TASK,
                clock.now(),
                STARTED,
                Priority.MEDIUM);

        executionService.executeWith(materialExecutionInfo);
    }

    public void processInsertMaterialDocument(final JsonObject inputJsonObject) {
        logger.info("process insertMaterialDocument Task..");
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                inputJsonObject,
                INSERT_MATERIAL_DOCUMENT_TASK,
                clock.now(),
                STARTED,
                Priority.MEDIUM);

        executionService.executeWith(materialExecutionInfo);
    }

    public void processCheckMaterialStatus(final JsonObject inputJsonObject) {
        logger.info("process checkMaterialStatus..");
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                inputJsonObject,
                CHECK_MATERIAL_STATUS_TASK,
                clock.now(),
                STARTED,
                Priority.MEDIUM);

        executionService.executeWith(materialExecutionInfo);
    }

    public void processAddCourtDocument(final JsonObject inputJsonObject) {
        logger.info("creating PROCESS_ADD_COURT_DOCUMENT_TASK from processAddCourtDocument method..");
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                inputJsonObject,
                PROCESS_ADD_COURT_DOCUMENT_TASK,
                clock.now(),
                STARTED,
                Priority.MEDIUM);

        executionService.executeWith(materialExecutionInfo);
    }

    public void initiateMaterialTaskForCase(final JsonObject inputJsonObject) {
        logger.info("creating INITIATE_MATERIAL_TASK_FOR_CASE from dcsNotificationTask..");
        final ExecutionInfo materialExecutionInfo = new ExecutionInfo(
                inputJsonObject,
                INITIATE_MATERIAL_TASK_FOR_CASE,
                clock.now().plusSeconds(parseInt(materialTasksDelayInSeconds)),
                STARTED,
                Priority.MEDIUM);

        executionService.executeWith(materialExecutionInfo);
    }

    public void updateTransactionStatus(final String transactionRefId, UpdateTransactionStatusRequest updateTransactionStatusRequest) {
        TransactionDetailEntity transaction = transactionDetailRepository.findByTransactionReferenceId(fromString(transactionRefId));
        if (isNull(transaction) || isNull(transaction.getTransactionRefId())) {
            throw new NotFoundException(format("Transaction not found for transactionRefId %s", transactionRefId));
        }

        if (!SENT.name().equalsIgnoreCase(transaction.getTransactionStatus())) {
            throw new DcsResponseProcessingException(format("Transaction is not in right status to update transactionRefId %s", transactionRefId));
        }

        if (nonNull(updateTransactionStatusRequest.getErrorPayload())) {
            ErrorPayload errorPayload = updateTransactionStatusRequest.getErrorPayload();
            updateFailedTransactionStatus(transactionRefId, errorPayload.getErrorCode().getValue(), errorPayload.getErrorMessage());

            unlinkByCaseIdIfErrorsPresent(errorPayload.getErrorCode().getValue(), transaction.getCaseId());
        }

        if (nonNull(updateTransactionStatusRequest.getSuccessPayload())) {
            updateSuccessTransactionStatus(transactionRefId);
        }

    }

    public static boolean isDefendantLevel(final CourtDocument courtDocument) {
        boolean isDefendantLevel = false;
        if (nonNull(courtDocument.getDocumentCategory().getDefendantDocument())) {
            isDefendantLevel = true;
        }

        if (nonNull(courtDocument.getDocumentCategory().getNowDocument())) {
            final NowDocument document = courtDocument.getDocumentCategory().getNowDocument();
            if (nonNull(document.getDefendantId())) {
                isDefendantLevel = true;
            }
        }
        return isDefendantLevel;
    }

    public static String getDocumentName(final Material material, final CourtDocument courtDocument) {
        String fileName = isNotEmpty(material.getName()) ? material.getName() : courtDocument.getName();
        if (getAllowedFileExtensionList().stream().anyMatch(extension -> fileName.toLowerCase().endsWith(extension))) {
            return fileName;
        } else {
            final String mimeType = courtDocument.getMimeType();
            final String extension = getAllowedFileExtension(mimeType);
            return fileName.concat(extension);
        }
    }

    public static String getAllowedFileExtension(final String mimeType) {
        String extension = getAllowedFileExtensionList().stream()
                .filter(fileExtension -> {
                    final String replacedExtension = fileExtension.replace(DOT_STRING, FORWARD_SLASH);
                    return mimeType.toLowerCase().contains(replacedExtension);
                }).findFirst().orElse(EMPTY);
        if (isEmpty(extension) && mimeType.toLowerCase().contains(MIME_TYPE_FOR_TEXT_EXTENSION)) {
            extension = DOT_TXT;
        }
        if (isEmpty(extension) && mimeType.toLowerCase().contains(MIME_TYPE_FOR_DOC_EXTENSION)) {
            extension = DOT_DOC;
        }
        if (isEmpty(extension) && mimeType.toLowerCase().contains(MIME_TYPE_FOR_DOCX_EXTENSION)) {
            extension = DOT_DOCX;
        }
        return extension;
    }
    @Transactional(REQUIRES_NEW)
    public void insertDocumentData(final MaterialTaskData materialTaskData) {
        if (materialTaskData.isCaseLevel()) {
            final CaseDocumentEntity caseDocumentEntity = new CaseDocumentEntity();
            caseDocumentEntity.setCaseId(fromString(materialTaskData.getCaseId()));
            caseDocumentEntity.setMaterialId(fromString(materialTaskData.getMaterialId()));
            caseDocumentRepository.save(caseDocumentEntity);
        }

        if (materialTaskData.isDefendantLevel()) {
            materialTaskData.getDefendantIdReferralIdMap().keySet().forEach(defendantId -> {
                final DefendantDocumentEntity defendantDocumentEntity = new DefendantDocumentEntity();
                defendantDocumentEntity.setDefendantId(fromString(defendantId));
                defendantDocumentEntity.setCaseId(fromString(materialTaskData.getCaseId()));
                defendantDocumentEntity.setMaterialId(fromString(materialTaskData.getMaterialId()));
                defendantDocumentRepository.save(defendantDocumentEntity);
            });
        }
    }

    public boolean isDocumentDataPresentAndRemoveDuplicates(final MaterialTaskData materialTaskData) {
        final UUID caseId = fromString(materialTaskData.getCaseId());
        final UUID materialId = fromString(materialTaskData.getMaterialId());
        if (materialTaskData.isCaseLevel()) {
            List<CaseDocumentEntity> caseDocumentEntityList = caseDocumentRepository.findByCaseIdAndMaterialId(caseId, materialId);
            return isNotEmpty(caseDocumentEntityList);
        }

        if (materialTaskData.isDefendantLevel()) {
            List<String> defendantIdList = materialTaskData.getDefendantIdReferralIdMap().keySet().stream().toList();
            for (String defendantId : defendantIdList) {
                List<DefendantDocumentEntity> defendantDocumentEntityList = defendantDocumentRepository.findByCaseIdMaterialIdAndDefendantId(caseId, materialId, fromString(defendantId));
                if(isNotEmpty(defendantDocumentEntityList)){
                    materialTaskData.getDefendantIdReferralIdMap().remove(defendantId);
                }
            }
            return isEmpty(materialTaskData.getDefendantIdReferralIdMap());
        }
        return false;
    }

    public boolean isMaterialEligibleForDistribution(final String documentName) {
        return isNotEmpty(documentName) && !documentName.toLowerCase().contains(STRING_NO_DISTRIBUTION.toLowerCase());
    }

    public boolean shouldSendMaterialToDcs(MaterialTaskData taskData) {
        return !isDocumentDataPresentAndRemoveDuplicates(taskData) && isMaterialEligibleForDistribution(taskData.getDocumentName());
    }

    private void updateFailedTransactionStatus(final String transactionRefId, final String errorCode, final String errorMessage) {
        final UUID tranId = fromString(transactionRefId);
        final String responseErr = format("%s: %s", errorCode, errorMessage);
        transactionMetadataRepository.updateStatusByTransactionReferenceId(TransactionStatus.FAILED.name(), tranId);
        transactionDetailRepository.updateStatusByTransactionReferenceId(TransactionStatus.FAILED.name(), responseErr, tranId);

    }

    private void updateSuccessTransactionStatus(final String transactionRefId) {
        final UUID tranId = fromString(transactionRefId);
        transactionMetadataRepository.updateStatusByTransactionReferenceId(SUCCESS.name(), tranId);
        transactionDetailRepository.updateStatusByTransactionReferenceId(SUCCESS.name(), EMPTY, tranId);
        try {
            TransactionDetailEntity transactionDetail = transactionDetailRepository.findByTransactionReferenceId(tranId);
            if (MATERIAL_UPDATE.name().equalsIgnoreCase(transactionDetail.getTransactionType())) {
                final JsonObject payloadJsonObject = stringToJsonObjectConverter.convert(transactionDetail.getPayload());
                final String documentName = payloadJsonObject.getString(DOCUMENT_NAME);
                final boolean isBlobDeleted = azureStorageService.deleteMaterialFromAzureStorage(documentName, transactionDetail.getTransactionRefId().toString());
                if (!isBlobDeleted) {
                    logger.error("Unable to delete the blob from azure blob storage for document name {} and transactionId {}", documentName, transactionRefId);
                }
            }
        } catch (RuntimeException ex) {
            logger.error(format("Failed to process deletion of blob from azure blob store for transactionId %s", transactionRefId), ex.getMessage());
        }
    }

    private boolean isCaseStatusEligibleForUnlink(final String status){
        return status.equalsIgnoreCase(LINKED.name()) ;
    }
    private static List<String> getAllowedFileExtensionList(){
        return Arrays.asList(
                DOT_DOC,DOT_DOCX,".jpg",".jpeg",".pdf", DOT_TXT
        );
    }

}

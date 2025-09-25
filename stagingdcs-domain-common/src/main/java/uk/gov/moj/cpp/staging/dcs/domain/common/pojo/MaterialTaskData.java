package uk.gov.moj.cpp.staging.dcs.domain.common.pojo;

import java.util.Map;

public class MaterialTaskData {

    private String caseId;
    private String caseUrn;
    private String caseReferralId;
    private Map<String,String> defendantIdReferralIdMap;
    private String tranRefId;
    private String materialId;
    private String documentTypeAccessId;
    private String documentName;
    private String documentDate;
    private String uploadedByUser;
    private String documentSection;
    private String azureStorageUrl;
    private boolean isCaseLevel;
    private boolean isDefendantLevel;

    public String getCaseUrn() {
        return caseUrn;
    }

    public void setCaseUrn(final String caseUrn) {
        this.caseUrn = caseUrn;
    }

    public String getDocumentTypeAccessId() {
        return documentTypeAccessId;
    }

    public void setDocumentTypeAccessId(final String documentTypeAccessId) {
        this.documentTypeAccessId = documentTypeAccessId;
    }

    public boolean isCaseLevel() {
        return isCaseLevel;
    }

    public void setCaseLevel(final boolean caseLevel) {
        isCaseLevel = caseLevel;
    }

    public boolean isDefendantLevel() {
        return isDefendantLevel;
    }

    public void setDefendantLevel(final boolean defendantLevel) {
        isDefendantLevel = defendantLevel;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(final String caseId) {
        this.caseId = caseId;
    }

    public String getCaseReferralId() {
        return caseReferralId;
    }

    public void setCaseReferralId(final String caseReferralId) {
        this.caseReferralId = caseReferralId;
    }

    public Map<String, String> getDefendantIdReferralIdMap() {
        return defendantIdReferralIdMap;
    }

    public void setDefendantIdReferralIdMap(final Map<String, String> defendantIdReferralIdMap) {
        this.defendantIdReferralIdMap = defendantIdReferralIdMap;
    }

    public String getTranRefId() {
        return tranRefId;
    }

    public void setTranRefId(final String tranRefId) {
        this.tranRefId = tranRefId;
    }

    public String getMaterialId() {
        return materialId;
    }

    public void setMaterialId(final String materialId) {
        this.materialId = materialId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(final String documentName) {
        this.documentName = documentName;
    }

    public String getDocumentDate() {
        return documentDate;
    }

    public void setDocumentDate(final String documentDate) {
        this.documentDate = documentDate;
    }

    public String getUploadedByUser() {
        return uploadedByUser;
    }

    public void setUploadedByUser(final String uploadedByUser) {
        this.uploadedByUser = uploadedByUser;
    }

    public String getDocumentSection() {
        return documentSection;
    }

    public void setDocumentSection(final String documentSection) {
        this.documentSection = documentSection;
    }

    public String getAzureStorageUrl() {
        return azureStorageUrl;
    }

    public void setAzureStorageUrl(final String azureStorageUrl) {
        this.azureStorageUrl = azureStorageUrl;
    }
}

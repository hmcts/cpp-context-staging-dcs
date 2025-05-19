package uk.gov.moj.cpp.staging.dcs.persistance.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "dcs_case_detail")
public class DcsCaseDetailEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 475984555273163583L;
    @Id
    private UUID id;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "case_urn")
    private String caseUrn;

    @Column(name = "case_referral_id")
    private UUID caseRefId;

    @Column(name = "defendant_id")
    private UUID defendantId;

    @Column(name = "defendant_referral_id")
    private UUID defendantRefId;

    @Column(name = "dcs_case_status")
    private String dcsCaseStatus;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime lastUpdatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(final UUID caseId) {
        this.caseId = caseId;
    }

    public String getCaseUrn() {
        return caseUrn;
    }

    public void setCaseUrn(final String caseUrn) {
        this.caseUrn = caseUrn;
    }

    public UUID getCaseRefId() {
        return caseRefId;
    }

    public void setCaseRefId(final UUID caseRefId) {
        this.caseRefId = caseRefId;
    }

    public UUID getDefendantId() {
        return defendantId;
    }

    public void setDefendantId(final UUID defendantId) {
        this.defendantId = defendantId;
    }

    public UUID getDefendantRefId() {
        return defendantRefId;
    }

    public void setDefendantRefId(final UUID defendantRefId) {
        this.defendantRefId = defendantRefId;
    }

    public String getDcsCaseStatus() {
        return dcsCaseStatus;
    }

    public void setDcsCaseStatus(final String dcsCaseStatus) {
        this.dcsCaseStatus = dcsCaseStatus;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(final ZonedDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }
}

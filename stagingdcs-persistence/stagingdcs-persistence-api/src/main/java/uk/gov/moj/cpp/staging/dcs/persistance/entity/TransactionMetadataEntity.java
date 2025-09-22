package uk.gov.moj.cpp.staging.dcs.persistance.entity;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "transaction_metadata")
public class TransactionMetadataEntity implements Serializable {
    @Id
    private UUID id;

    @Column(name = "transaction_ref_id")
    private UUID transactionRefId;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "defendant_id")
    private UUID defendantId;

    @Column(name = "material_id")
    private UUID materialId;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @Column(name = "transaction_status")
    private String transactionStatus;

    @Column(name = "transaction_type")
    private String transactionType;

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getTransactionRefId() {
        return transactionRefId;
    }

    public void setTransactionRefId(final UUID transactionRefId) {
        this.transactionRefId = transactionRefId;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(final UUID caseId) {
        this.caseId = caseId;
    }

    public UUID getDefendantId() {
        return defendantId;
    }

    public void setDefendantId(final UUID defendantId) {
        this.defendantId = defendantId;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public void setMaterialId(final UUID materialId) {
        this.materialId = materialId;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getTransactionStatus() {
        return transactionStatus;
    }

    public void setTransactionStatus(final String transactionStatus) {
        this.transactionStatus = transactionStatus;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(final String transactionType) {
        this.transactionType = transactionType;
    }
}

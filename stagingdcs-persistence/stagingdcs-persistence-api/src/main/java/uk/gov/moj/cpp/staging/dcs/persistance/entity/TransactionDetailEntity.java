package uk.gov.moj.cpp.staging.dcs.persistance.entity;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transaction_detail")
public class TransactionDetailEntity implements Serializable {
    @Id
    @Column(name = "transaction_ref_id")
    private UUID transactionRefId;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "payload")
    private String payload;

    @Column(name = "error")
    private String error;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @Column(name = "transaction_status")
    private String transactionStatus;

    @Column(name = "transaction_type")
    private String transactionType;

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(final UUID caseId) {
        this.caseId = caseId;
    }

    public UUID getTransactionRefId() {
        return transactionRefId;
    }

    public void setTransactionRefId(final UUID transactionRefId) {
        this.transactionRefId = transactionRefId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(final String payload) {
        this.payload = payload;
    }

    public String getError() {
        return error;
    }

    public void setError(final String error) {
        this.error = error;
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

package uk.gov.moj.cpp.staging.dcs.persistance.entity;

import java.io.Serializable;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "case_defendant_offences")
public class CaseDefendantOffencesEntity implements Serializable {
    @Id
    private UUID id;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "defendant_id")
    private UUID defendantId;

    @Column(name = "offence_id")
    private UUID offenceId;

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

    public UUID getDefendantId() {
        return defendantId;
    }

    public void setDefendantId(final UUID defendantId) {
        this.defendantId = defendantId;
    }

    public UUID getOffenceId() {
        return offenceId;
    }

    public void setOffenceId(final UUID offenceId) {
        this.offenceId = offenceId;
    }
}

package uk.gov.moj.cpp.staging.dcs.persistance.entity;

import uk.gov.moj.cpp.staging.dcs.persistance.pojos.DefendantDocument;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

@Entity
@Table(name = "defendant_document")
@IdClass(DefendantDocument.class)
public class DefendantDocumentEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 475984950073161583L;

    @Id
    @Column(name = "case_id")
    private UUID caseId;

    @Id
    @Column(name = "defendant_id")
    private UUID defendantId;

    @Id
    @Column(name = "material_id")
    private UUID materialId;

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
}

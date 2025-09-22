package uk.gov.moj.cpp.staging.dcs.persistance.entity;

import uk.gov.moj.cpp.staging.dcs.persistance.pojos.CaseDocument;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

@Entity
@Table(name = "case_document")
@IdClass(CaseDocument.class)
public class CaseDocumentEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 275081550073168583L;

    @Id
    @Column(name = "case_id")
    private UUID caseId;

    @Id
    @Column(name = "material_id")
    private UUID materialId;

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(final UUID caseId) {
        this.caseId = caseId;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public void setMaterialId(final UUID materialId) {
        this.materialId = materialId;
    }
}

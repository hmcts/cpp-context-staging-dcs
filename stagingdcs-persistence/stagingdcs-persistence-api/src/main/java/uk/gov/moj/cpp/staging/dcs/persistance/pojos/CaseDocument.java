package uk.gov.moj.cpp.staging.dcs.persistance.pojos;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;

public class CaseDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 175984550073163583L;

    @Column(name = "case_id")
    private UUID caseId;

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

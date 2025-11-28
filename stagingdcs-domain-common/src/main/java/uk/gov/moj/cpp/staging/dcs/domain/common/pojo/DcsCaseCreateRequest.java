package uk.gov.moj.cpp.staging.dcs.domain.common.pojo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;

public class DcsCaseCreateRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 5002347079153788014L;
    private UUID caseId;
    private String caseUrn;
    private String prosecutionAuthority;
    private List<DcsDefendant> defendants;

    @JsonCreator
    public DcsCaseCreateRequest(final UUID caseId, final String caseUrn, final String prosecutionAuthority, final String courtCentre, final LocalDate hearingDate, final List<DcsDefendant> defendants) {
        this.caseId = caseId;
        this.caseUrn = caseUrn;
        this.prosecutionAuthority = prosecutionAuthority;
        this.defendants = defendants;
    }

    private DcsCaseCreateRequest(final Builder builder) {
        caseId = builder.caseId;
        caseUrn = builder.caseUrn;
        prosecutionAuthority = builder.prosecutionAuthority;
        defendants = builder.defendants;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public String getCaseUrn() {
        return caseUrn;
    }

    public String getProsecutionAuthority() {
        return prosecutionAuthority;
    }

    public List<DcsDefendant> getDefendants() {
        return defendants;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final DcsCaseCreateRequest that = (DcsCaseCreateRequest) o;
        return Objects.equals(caseId, that.caseId) && Objects.equals(caseUrn, that.caseUrn) && Objects.equals(prosecutionAuthority, that.prosecutionAuthority) && Objects.equals(defendants, that.defendants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseId, caseUrn, prosecutionAuthority, defendants);
    }


    public static final class Builder {
        private UUID caseId;
        private String caseUrn;
        private String prosecutionAuthority;
        private List<DcsDefendant> defendants;

        private Builder() {
        }

        public static Builder newDcsCaseCreateRequest() {
            return new Builder();
        }

        public Builder withCaseId(final UUID val) {
            caseId = val;
            return this;
        }

        public Builder withCaseUrn(final String val) {
            caseUrn = val;
            return this;
        }

        public Builder withProsecutionAuthority(final String val) {
            prosecutionAuthority = val;
            return this;
        }

        public Builder withDefendants(final List<DcsDefendant> val) {
            defendants = val;
            return this;
        }

        public DcsCaseCreateRequest build() {
            return new DcsCaseCreateRequest(this);
        }
    }
}

package uk.gov.moj.cpp.staging.dcs.domain.common.pojo;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;

public class DcsOffence implements Serializable {

    private UUID offenceId;
    private String offenceCode;

    @JsonCreator
    public DcsOffence(final UUID offenceId, final String offenceCode) {
        this.offenceId = offenceId;
        this.offenceCode = offenceCode;
    }

    private DcsOffence(final Builder builder) {
        offenceId = builder.offenceId;
        offenceCode = builder.offenceCode;
    }

    public UUID getOffenceId() {
        return offenceId;
    }

    public String getOffenceCode() {
        return offenceCode;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final DcsOffence dcsOffence = (DcsOffence) o;
        return Objects.equals(offenceId, dcsOffence.offenceId) && Objects.equals(offenceCode, dcsOffence.offenceCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offenceId, offenceCode);
    }

    public static final class Builder {
        private UUID offenceId;
        private String offenceCode;

        private Builder() {
        }

        public static Builder newOffence() {
            return new Builder();
        }

        public Builder withOffenceId(final UUID value) {
            offenceId = value;
            return this;
        }

        public Builder withOffenceCode(final String value) {
            offenceCode = value;
            return this;
        }

        public DcsOffence build() {
            return new DcsOffence(this);
        }
    }
}

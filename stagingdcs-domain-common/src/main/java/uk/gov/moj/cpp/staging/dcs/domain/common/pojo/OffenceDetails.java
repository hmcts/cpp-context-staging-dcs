package uk.gov.moj.cpp.staging.dcs.domain.common.pojo;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;

public class OffenceDetails implements Serializable {
    private List<DcsOffence> addedOffences;
    private List<DcsOffence> removedOffences;

    @JsonCreator
    public OffenceDetails(final List<DcsOffence> addedOffences, final List<DcsOffence> removedOffences) {
        this.addedOffences = addedOffences;
        this.removedOffences = removedOffences;
    }

    private OffenceDetails(final OffenceDetails.Builder builder) {
        addedOffences = builder.addedOffences;
        removedOffences = builder.removedOffences;
    }

    public List<DcsOffence> getAddedOffences() {
        return addedOffences;
    }

    public List<DcsOffence> getRemovedOffences() {
        return removedOffences;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final OffenceDetails offenceDetails = (OffenceDetails) o;
        return Objects.equals(addedOffences, offenceDetails.addedOffences) && Objects.equals(removedOffences, offenceDetails.removedOffences);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addedOffences, removedOffences);
    }

    public static final class Builder {
        private List<DcsOffence> addedOffences;
        private List<DcsOffence> removedOffences;

        private Builder() {
        }

        public static OffenceDetails.Builder newOffence() {
            return new OffenceDetails.Builder();
        }

        public OffenceDetails.Builder withAddedOffences(final List<DcsOffence> value) {
            addedOffences = value;
            return this;
        }

        public OffenceDetails.Builder withRemovedOffences(final List<DcsOffence> value) {
            removedOffences = value;
            return this;
        }

        public OffenceDetails build() {
            return new OffenceDetails(this);
        }
    }
}

package uk.gov.moj.cpp.staging.dcs.domain.common.pojo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;

public class DefendantOrganisation implements Serializable {
    @Serial
    private static final long serialVersionUID = 5002347079152288014L;
    private String name;

    private DefendantOrganisation(final Builder builder) {
        name = builder.name;
    }

    public String getName() {
        return name;
    }

    @JsonCreator
    public DefendantOrganisation(final String name) {
        this.name = name;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final DefendantOrganisation that = (DefendantOrganisation) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    public static final class Builder {
        private String name;

        private Builder() {
        }

        public static Builder newDefendantOrganisation() {
            return new Builder();
        }

        public Builder withName(final String val) {
            name = val;
            return this;
        }

        public DefendantOrganisation build() {
            return new DefendantOrganisation(this);
        }
    }
}

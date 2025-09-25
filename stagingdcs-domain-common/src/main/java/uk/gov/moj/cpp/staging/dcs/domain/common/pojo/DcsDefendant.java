package uk.gov.moj.cpp.staging.dcs.domain.common.pojo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;

public class DcsDefendant implements Serializable {

    @Serial
    private static final long serialVersionUID = 5872347079153788014L;

    private UUID id;
    private String bailStatus;
    private String interpreterLanguage;
    private String interpreterInformation;
    private DefendantPerson defendantPerson;
    private DefendantOrganisation defendantOrganisation;
    private OffenceDetails offencesDetails;

    @JsonCreator
    public DcsDefendant(final UUID id, final String bailStatus, final String interpreterLanguage, final String interpreterInformation, final DefendantPerson defendantPerson, final DefendantOrganisation defendantOrganisation, final OffenceDetails offencesDetails) {
        this.id = id;
        this.bailStatus = bailStatus;
        this.interpreterLanguage = interpreterLanguage;
        this.interpreterInformation = interpreterInformation;
        this.defendantPerson = defendantPerson;
        this.defendantOrganisation = defendantOrganisation;
        this.offencesDetails = offencesDetails;
    }

    private DcsDefendant(final Builder builder) {
        id = builder.id;
        bailStatus = builder.bailStatus;
        interpreterLanguage = builder.interpreterLanguage;
        interpreterInformation = builder.interpreterInformation;
        defendantPerson = builder.defendantPerson;
        defendantOrganisation = builder.defendantOrganisation;
        offencesDetails = builder.offencesDetails;
    }

    public UUID getId() {
        return id;
    }

    public String getBailStatus() {
        return bailStatus;
    }

    public String getInterpreterLanguage() {
        return interpreterLanguage;
    }

    public String getInterpreterInformation() {  return interpreterInformation; }

    public DefendantPerson getDefendantPerson() {
        return defendantPerson;
    }

    public DefendantOrganisation getDefendantOrganisation() {
        return defendantOrganisation;
    }

    public OffenceDetails getOffencesDetails() {
        return offencesDetails;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final DcsDefendant defendant = (DcsDefendant) o;
        return Objects.equals(id, defendant.id) && Objects.equals(bailStatus, defendant.bailStatus) && Objects.equals(interpreterLanguage, defendant.interpreterLanguage) && Objects.equals(defendantPerson, defendant.defendantPerson) && Objects.equals(defendantOrganisation, defendant.defendantOrganisation) && Objects.equals(offencesDetails, defendant.offencesDetails) && Objects.equals(interpreterInformation, defendant.interpreterInformation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, bailStatus, interpreterLanguage, defendantPerson, defendantOrganisation, offencesDetails);
    }


    public static final class Builder {
        private UUID id;
        private String bailStatus;
        private String interpreterLanguage;
        private String interpreterInformation;
        private DefendantPerson defendantPerson;
        private DefendantOrganisation defendantOrganisation;
        private OffenceDetails offencesDetails;

        private Builder() {
        }

        public static Builder newDefendant() {
            return new Builder();
        }

        public Builder withId(final UUID val) {
            id = val;
            return this;
        }

        public Builder withBailStatus(final String val) {
            bailStatus = val;
            return this;
        }

        public Builder withInterpreterLanguage(final String val) {
            interpreterLanguage = val;
            return this;
        }

        public Builder withInterpreterInformation(final String val) {
            interpreterInformation = val;
            return this;
        }

        public Builder withDefendantPerson(final DefendantPerson val) {
            defendantPerson = val;
            return this;
        }

        public Builder withDefendantOrganisation(final DefendantOrganisation val) {
            defendantOrganisation = val;
            return this;
        }

        public Builder withOffencesDetails(final OffenceDetails val) {
            offencesDetails = val;
            return this;
        }

        public DcsDefendant build() {
            return new DcsDefendant(this);
        }
    }
}

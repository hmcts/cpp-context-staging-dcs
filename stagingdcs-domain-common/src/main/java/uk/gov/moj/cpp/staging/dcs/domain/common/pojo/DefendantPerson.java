package uk.gov.moj.cpp.staging.dcs.domain.common.pojo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;

public class DefendantPerson implements Serializable {
    @Serial
    private static final long serialVersionUID = 9002347079153788014L;
    private String forename;
    private String middleName;
    private String surname;
    private LocalDate dateOfBirth;

    @JsonCreator
    public DefendantPerson(final String forename, final String middleName, final String surname, final LocalDate dateOfBirth) {
        this.forename = forename;
        this.middleName = middleName;
        this.surname = surname;
        this.dateOfBirth = dateOfBirth;
    }

    private DefendantPerson(final Builder builder) {
        forename = builder.forename;
        middleName = builder.middleName;
        surname = builder.surname;
        dateOfBirth = builder.dateOfBirth;
    }

    public String getForename() {
        return forename;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getSurname() {
        return surname;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final DefendantPerson that = (DefendantPerson) o;
        return Objects.equals(forename, that.forename) && Objects.equals(middleName, that.middleName) && Objects.equals(surname, that.surname) && Objects.equals(dateOfBirth, that.dateOfBirth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(forename, middleName, surname, dateOfBirth);
    }


    public static final class Builder {
        private String forename;
        private String middleName;
        private String surname;
        private LocalDate dateOfBirth;

        private Builder() {
        }

        public static Builder newDefendantPerson() {
            return new Builder();
        }

        public Builder withForename(final String val) {
            forename = val;
            return this;
        }

        public Builder withMiddleName(final String val) {
            middleName = val;
            return this;
        }

        public Builder withSurname(final String val) {
            surname = val;
            return this;
        }

        public Builder withDateOfBirth(final LocalDate val) {
            dateOfBirth = val;
            return this;
        }

        public DefendantPerson build() {
            return new DefendantPerson(this);
        }
    }
}

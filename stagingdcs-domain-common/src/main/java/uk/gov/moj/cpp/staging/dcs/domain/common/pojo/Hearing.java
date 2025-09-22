package uk.gov.moj.cpp.staging.dcs.domain.common.pojo;

import java.io.Serializable;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonCreator;

public class Hearing implements Serializable {
    private String courtCentre;
    private LocalDate hearingDate;

    @JsonCreator
    public Hearing(final String courtCentre, final LocalDate hearingDate) {
        this.courtCentre = courtCentre;
        this.hearingDate = hearingDate;
    }

    private Hearing(final Hearing.Builder builder) {
        courtCentre = builder.courtCentre;
        hearingDate = builder.hearingDate;
    }

    public String getCourtCentre() {
        return courtCentre;
    }

    public LocalDate getHearingDate() {
        return hearingDate;
    }

    public static final class Builder {
        private String courtCentre;
        private LocalDate hearingDate;

        private Builder() {
        }

        public static Hearing.Builder newHearing() {
            return new Hearing.Builder();
        }

        public Hearing.Builder withCourtCentre(final String val) {
            courtCentre = val;
            return this;
        }

        public Hearing.Builder withHearingDate(final LocalDate val) {
            hearingDate = val;
            return this;
        }

        public Hearing build() {
            return new Hearing(this);
        }
    }
}

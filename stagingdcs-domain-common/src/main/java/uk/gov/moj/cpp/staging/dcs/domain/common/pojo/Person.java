package uk.gov.moj.cpp.staging.dcs.domain.common.pojo;

import java.time.LocalDate;

public record Person (
        LocalDate dateOfBirth,
        String firstName,
        String interpreterLanguageNeeds,
        String lastName,
        String middleName,
        String nationalInsuranceNumber,
        String title)  { }

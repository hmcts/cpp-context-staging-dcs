package uk.gov.moj.cpp.staging.dcs.domain.common.pojo;

import java.util.UUID;

public record Defendant(UUID id,
                        UUID masterDefendantId,
                        PersonDefendant personDefendant,
                        LegalEntityDefendant legalEntityDefendant
                        ) {
}

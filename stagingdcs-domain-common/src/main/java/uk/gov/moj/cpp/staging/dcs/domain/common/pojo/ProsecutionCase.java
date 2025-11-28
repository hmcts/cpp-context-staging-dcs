package uk.gov.moj.cpp.staging.dcs.domain.common.pojo;

import java.util.List;
import java.util.UUID;


public record ProsecutionCase(UUID id, List<Defendant> defendants, ProsecutionCaseIdentifier prosecutionCaseIdentifier) {
}
